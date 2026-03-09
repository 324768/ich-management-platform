package com.hyang.ich.omnitrix.controller;

import com.hyang.ich.common.vo.Result;
import com.hyang.ich.omnitrix.dto.*;
import com.hyang.ich.omnitrix.entity.AiConversation;
import com.hyang.ich.omnitrix.entity.AiMessage;
import com.hyang.ich.omnitrix.mapper.AiTraceLogMapper;
import com.hyang.ich.omnitrix.orchestrator.OrchestratorService;
import com.hyang.ich.omnitrix.service.ConversationService;
import com.hyang.ich.omnitrix.infrastructure.sse.SseEmitterManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.*;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/ai")
public class AiChatController {

    private final OrchestratorService orchestratorService;
    private final ConversationService conversationService;
    private final SseEmitterManager sseEmitterManager;
    private final Executor aiAsyncExecutor;
    private final AiTraceLogMapper traceLogMapper;

    public AiChatController(OrchestratorService orchestratorService,
                            ConversationService conversationService,
                            SseEmitterManager sseEmitterManager,
                            @org.springframework.beans.factory.annotation.Qualifier("aiAsyncExecutor") Executor aiAsyncExecutor,
                            AiTraceLogMapper traceLogMapper) {
        this.orchestratorService = orchestratorService;
        this.conversationService = conversationService;
        this.sseEmitterManager = sseEmitterManager;
        this.aiAsyncExecutor = aiAsyncExecutor;
        this.traceLogMapper = traceLogMapper;
    }

    /**
     * 同步聊天
     */
    @PostMapping("/chat")
    public Result<ChatResponse> chat(@RequestBody ChatRequest request,
                                     @RequestParam(defaultValue = "1") Long userId) {
        // Phase 1 暂时从请求参数获取 userId，Phase 2 改为 JWT 解析
        if (request.getSessionId() == null || request.getSessionId().isEmpty()) {
            request.setSessionId(UUID.randomUUID().toString().replace("-", ""));
        }

        ChatResponse response = orchestratorService.chat(userId, request.getSessionId(), request.getMessage());
        return Result.success(response);
    }

    /**
     * 流式聊天 (SSE)
     */
    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestParam String sessionId,
                                  @RequestParam String message,
                                  @RequestParam(defaultValue = "1") Long userId) {
        SseEmitter emitter = sseEmitterManager.create();

        // 使用线程池异步执行流式聊天
        aiAsyncExecutor.execute(() -> orchestratorService.chatStream(userId, sessionId, message, emitter));

        return emitter;
    }

    /**
     * 创建新对话
     */
    @PostMapping("/conversation/create")
    public Result<ConversationCreateVO> createConversation(
            @RequestParam(defaultValue = "1") Long userId,
            @RequestBody(required = false) Map<String, String> body) {
        String title = (body != null && body.containsKey("title")) ? body.get("title") : "新对话";
        AiConversation conv = conversationService.create(userId, title);
        return Result.success(ConversationCreateVO.from(conv));
    }

    /**
     * 获取用户对话列表
     */
    @GetMapping("/conversation/list")
    public Result<List<ConversationVO>> listConversations(
            @RequestParam(defaultValue = "1") Long userId) {
        List<AiConversation> conversations = conversationService.listByUserId(userId);
        List<ConversationVO> result = conversations.stream()
                .map(ConversationVO::from)
                .collect(Collectors.toList());
        return Result.success(result);
    }

    /**
     * 获取对话详情（含消息）
     */
    @GetMapping("/conversation/{id}")
    public Result<ConversationDetailVO> getConversation(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") Long userId) {
        AiConversation conv = conversationService.getById(id);
        if (conv == null || !conv.getUserId().equals(userId)) {
            return Result.failed("对话不存在");
        }

        List<AiMessage> messages = conversationService.listMessages(id);
        List<MessageVO> messageVOs = messages.stream()
                .map(MessageVO::from)
                .collect(Collectors.toList());
        return Result.success(ConversationDetailVO.from(conv, messageVOs));
    }

    /**
     * 删除对话
     */
    @DeleteMapping("/conversation/{id}")
    public Result<Void> deleteConversation(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") Long userId) {
        conversationService.deleteConversation(id, userId);
        return Result.success();
    }

    /**
     * 导出对话为 Markdown 文本
     */
    @GetMapping("/conversation/{id}/export")
    public Result<String> exportConversation(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") Long userId) {
        AiConversation conv = conversationService.getById(id);
        if (conv == null || !conv.getUserId().equals(userId)) {
            return Result.failed("对话不存在");
        }
        List<AiMessage> messages = conversationService.listMessages(id);
        StringBuilder md = new StringBuilder();
        md.append("# ").append(conv.getTitle() != null ? conv.getTitle() : "对话记录").append("\n\n");
        for (AiMessage msg : messages) {
            if ("user".equals(msg.getRole())) {
                md.append("**用户**: ").append(msg.getContent()).append("\n\n");
            } else if ("assistant".equals(msg.getRole())) {
                md.append("**Omnitrix AI**: ").append(msg.getContent()).append("\n\n---\n\n");
            }
        }
        return Result.success(md.toString());
    }

    /**
     * 重新生成：删除最后一条 AI 回复，用最后一条用户消息重新生成（流式）
     */
    @GetMapping(value = "/chat/regenerate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter regenerate(@RequestParam String sessionId,
                                  @RequestParam(defaultValue = "1") Long userId) {
        SseEmitter emitter = sseEmitterManager.create();

        aiAsyncExecutor.execute(() -> {
            try {
                // 查找最后一条用户消息
                com.hyang.ich.omnitrix.entity.AiMessage lastUserMsg =
                        conversationService.getLastMessage(sessionId, "user");
                if (lastUserMsg == null) {
                    sseEmitterManager.sendError(emitter, "没有可重新生成的消息");
                    return;
                }

                // 删除最后一条 AI 回复（如果存在）
                com.hyang.ich.omnitrix.entity.AiMessage lastAssistantMsg =
                        conversationService.getLastMessage(sessionId, "assistant");
                if (lastAssistantMsg != null &&
                        lastAssistantMsg.getCreateTime().after(lastUserMsg.getCreateTime())) {
                    conversationService.deleteMessage(lastAssistantMsg.getId());
                }

                // 用最后一条用户消息重新走流式生成
                orchestratorService.chatStream(userId, sessionId, lastUserMsg.getContent(), emitter);
            } catch (Exception e) {
                sseEmitterManager.sendError(emitter, "重新生成失败");
            }
        });

        return emitter;
    }

    /**
     * 用户反馈：对 AI 回复点赞/踩
     * @param messageId AI 消息 ID
     * @param feedback  1=点赞, -1=踩
     */
    @PostMapping("/feedback/{messageId}")
    public Result<Void> feedback(@PathVariable Long messageId,
                                  @RequestParam int feedback) {
        if (feedback != 1 && feedback != -1) {
            return Result.failed("feedback 只能为 1 或 -1");
        }
        try {
            String traceId = traceLogMapper.selectTraceIdByMessageId(messageId);
            if (traceId == null) {
                return Result.failed("未找到对应追踪记录");
            }
            traceLogMapper.updateUserFeedback(traceId, feedback);
            log.info("用户反馈: messageId={}, feedback={}, traceId={}", messageId, feedback, traceId);
            return Result.success();
        } catch (Exception e) {
            log.error("用户反馈保存失败: {}", e.getMessage());
            return Result.failed("反馈保存失败");
        }
    }
}
