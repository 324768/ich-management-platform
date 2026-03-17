package com.hyang.ich.omnitrix.brain.tools;

import com.hyang.ich.omnitrix.agent.tool.ToolRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 工具注册配置 — 在 Spring 容器启动后，将各 Tool 提供者注册到 ToolRegistry。
 * <p>
 * 角色映射:
 * - "user" → ContentTools, CommerceTools, UserTools, KnowledgeTools, RecommendTools, BrowseHistoryTools
 * - "admin" → ContentTools, CommerceTools, AdminTools, KnowledgeTools
 * - "ultra" → (继承 admin 全部) + UltraTools
 */
@Slf4j
@Component
public class ToolRegistrationConfig {

    private final ToolRegistry toolRegistry;
    private final ContentTools contentTools;
    private final CommerceTools commerceTools;
    private final UserTools userTools;
    private final KnowledgeTools knowledgeTools;
    private final RecommendTools recommendTools;
    private final BrowseHistoryTools browseHistoryTools;
    private final AdminTools adminTools;
    private final UltraTools ultraTools;

    public ToolRegistrationConfig(ToolRegistry toolRegistry,
                                   ContentTools contentTools,
                                   CommerceTools commerceTools,
                                   UserTools userTools,
                                   KnowledgeTools knowledgeTools,
                                   RecommendTools recommendTools,
                                   BrowseHistoryTools browseHistoryTools,
                                   AdminTools adminTools,
                                   UltraTools ultraTools) {
        this.toolRegistry = toolRegistry;
        this.contentTools = contentTools;
        this.commerceTools = commerceTools;
        this.userTools = userTools;
        this.knowledgeTools = knowledgeTools;
        this.recommendTools = recommendTools;
        this.browseHistoryTools = browseHistoryTools;
        this.adminTools = adminTools;
        this.ultraTools = ultraTools;
    }

    @PostConstruct
    public void registerAll() {
        // ===== User 角色工具 =====
        toolRegistry.register("user", contentTools);
        toolRegistry.register("user", commerceTools);
        toolRegistry.register("user", userTools);
        toolRegistry.register("user", knowledgeTools);
        toolRegistry.register("user", recommendTools);
        toolRegistry.register("user", browseHistoryTools);

        // ===== Admin 角色工具 =====
        toolRegistry.register("admin", contentTools);
        toolRegistry.register("admin", commerceTools);
        toolRegistry.register("admin", knowledgeTools);
        toolRegistry.register("admin", adminTools);

        // ===== Ultra 角色工具（继承 admin + ultra 专属） =====
        toolRegistry.register("ultra", contentTools);
        toolRegistry.register("ultra", commerceTools);
        toolRegistry.register("ultra", knowledgeTools);
        toolRegistry.register("ultra", adminTools);
        toolRegistry.register("ultra", ultraTools);

        log.info("ToolRegistry 初始化完成: {}", toolRegistry.getStats());
    }
}
