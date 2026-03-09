package com.hyang.ich.omnitrix.controller;

import com.hyang.ich.common.vo.Result;
import com.hyang.ich.omnitrix.dto.ChatRequest;
import com.hyang.ich.omnitrix.dto.ChatResponse;
import com.hyang.ich.omnitrix.orchestrator.OrchestratorService;
import com.hyang.ich.omnitrix.service.UltraAuthService;
import com.hyang.ich.omnitrix.infrastructure.sse.SseEmitterManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * Omnitrix Ultra AI Controller
 * 最高权限 AI 管理端点，需要 Ultra Token 认证。
 */
@Slf4j
@RestController
@RequestMapping("/api/ai/ultra")
public class AiUltraController {

    private final OrchestratorService orchestratorService;
    private final UltraAuthService ultraAuthService;
    private final SseEmitterManager sseEmitterManager;
    private final Executor aiAsyncExecutor;

    public AiUltraController(OrchestratorService orchestratorService,
                              UltraAuthService ultraAuthService,
                              SseEmitterManager sseEmitterManager,
                              @org.springframework.beans.factory.annotation.Qualifier("aiAsyncExecutor") Executor aiAsyncExecutor) {
        this.orchestratorService = orchestratorService;
        this.ultraAuthService = ultraAuthService;
        this.sseEmitterManager = sseEmitterManager;
        this.aiAsyncExecutor = aiAsyncExecutor;
    }

    /**
     * Ultra 密码验证
     */
    @PostMapping("/auth")
    public Result<Map<String, String>> authenticate(@RequestBody Map<String, String> body,
                                                     @RequestParam(defaultValue = "1") Long adminId) {
        String password = body != null ? body.get("password") : null;
        if (password == null || password.isEmpty()) {
            return Result.failed(400, "请输入密码");
        }

        if (ultraAuthService.isLocked(adminId)) {
            return Result.failed(423, "认证已被锁定，请30分钟后再试");
        }

        String token = ultraAuthService.verify(password, adminId);
        if (token == null) {
            return Result.failed(401, "密码错误");
        }

        Map<String, String> data = new HashMap<>();
        data.put("ultraToken", token);
        log.info("Ultra认证成功: adminId={}", adminId);
        return Result.success(data);
    }

    /**
     * Ultra 同步聊天
     */
    @PostMapping("/chat")
    public Result<ChatResponse> chat(@RequestBody ChatRequest request,
                                      @RequestParam(defaultValue = "1") Long adminId,
                                      @RequestHeader("X-Ultra-Token") String ultraToken) {
        // 验证 Ultra Token
        if (!ultraAuthService.validateToken(ultraToken)) {
            return Result.failed(401, "Ultra Token 无效或已过期，请重新认证");
        }

        Long tokenAdminId = ultraAuthService.getAdminIdFromToken(ultraToken);
        if (tokenAdminId != null) adminId = tokenAdminId;

        if (request.getSessionId() == null || request.getSessionId().isEmpty()) {
            request.setSessionId("ultra_" + UUID.randomUUID().toString().replace("-", ""));
        }

        ChatResponse response = orchestratorService.ultraChat(adminId, request.getSessionId(), request.getMessage());
        return Result.success(response);
    }

    /**
     * Ultra 流式聊天 (SSE)
     */
    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestParam String sessionId,
                                  @RequestParam String message,
                                  @RequestParam(defaultValue = "1") Long adminId,
                                  @RequestParam String ultraToken) {
        // SSE 不支持自定义请求头，通过 query param 传递 token
        if (!ultraAuthService.validateToken(ultraToken)) {
            SseEmitter emitter = sseEmitterManager.create();
            sseEmitterManager.sendError(emitter, "Ultra Token 无效或已过期，请重新认证");
            return emitter;
        }

        Long tokenAdminId = ultraAuthService.getAdminIdFromToken(ultraToken);
        if (tokenAdminId != null) adminId = tokenAdminId;

        SseEmitter emitter = sseEmitterManager.create();
        final Long finalAdminId = adminId;
        aiAsyncExecutor.execute(() -> orchestratorService.ultraChatStream(finalAdminId, sessionId, message, emitter));
        return emitter;
    }

    /**
     * 验证 Ultra Token 是否有效
     */
    @GetMapping("/validate")
    public Result<Map<String, Object>> validateToken(@RequestParam String ultraToken) {
        boolean valid = ultraAuthService.validateToken(ultraToken);
        Map<String, Object> data = new HashMap<>();
        data.put("valid", valid);
        if (valid) {
            Long adminId = ultraAuthService.getAdminIdFromToken(ultraToken);
            data.put("adminId", adminId);
        }
        return Result.success(data);
    }

    /**
     * 退出 Ultra 模式（撤销 token）
     */
    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader(value = "X-Ultra-Token", required = false) String ultraToken) {
        if (ultraToken != null) {
            ultraAuthService.revokeToken(ultraToken);
        }
        return Result.success(null);
    }
}
