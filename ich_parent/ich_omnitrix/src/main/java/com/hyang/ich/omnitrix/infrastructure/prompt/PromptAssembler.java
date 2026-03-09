package com.hyang.ich.omnitrix.infrastructure.prompt;

import com.hyang.ich.omnitrix.agent.AgentContext;
import com.hyang.ich.omnitrix.agent.SubAgent;
import com.hyang.ich.omnitrix.dto.AgentQueryResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

@Slf4j
@Component
public class PromptAssembler {

    private final PromptManager promptManager;

    public PromptAssembler(PromptManager promptManager) {
        this.promptManager = promptManager;
    }

    /**
     * 组装完整的 System Prompt（向后兼容，无用户画像）
     */
    public String assemble(AgentContext context, SubAgent subAgent,
                           AgentQueryResult queryResult, String summary) {
        return assemble(context, subAgent, queryResult, summary, null);
    }

    /**
     * 组装完整的 System Prompt（含用户画像）
     *
     * @param context      用户上下文
     * @param subAgent     选中的子代理（可为null，表示GeneralSubAgent）
     * @param queryResult  子代理查询结果（可为null）
     * @param summary      对话摘要（可为null）
     * @param userProfile  用户画像文本（可为null，来自 UserMemoryService）
     * @return 完整的 System Prompt
     */
    public String assemble(AgentContext context, SubAgent subAgent,
                           AgentQueryResult queryResult, String summary,
                           String userProfile) {
        return assemble(context, subAgent, queryResult, summary, userProfile, null);
    }

    /**
     * 组装完整的 System Prompt（含用户画像 + 系统记忆L4）
     *
     * @param systemMemory 系统全局记忆文本（可为null，来自 SystemMemoryService）
     */
    public String assemble(AgentContext context, SubAgent subAgent,
                           AgentQueryResult queryResult, String summary,
                           String userProfile, String systemMemory) {

        StringBuilder sb = new StringBuilder();

        // 层级1: 基础 Prompt
        sb.append(promptManager.resolve("master_brain_system"));

        // 层级2a: 当前时间
        LocalDateTime now = LocalDateTime.now();
        String dayOfWeek = now.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.CHINA);
        sb.append("\n\n## 当前时间\n");
        sb.append("今天是 ").append(now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        sb.append("（").append(dayOfWeek).append("），");
        sb.append("当前时间 ").append(now.format(DateTimeFormatter.ofPattern("HH:mm"))).append("。");

        // 层级2b: 用户身份信息
        if (context != null && context.getUserId() != null) {
            sb.append("\n\n## 当前用户信息\n");
            sb.append("- 用户ID: ").append(context.getUserId()).append("\n");
            if (StringUtils.isNotBlank(context.getUserRole())) {
                sb.append("- 角色: ").append(context.getUserRole()).append("\n");
            }
        }

        // 层级2b+: 用户画像（跨会话L3长期记忆）
        if (StringUtils.isNotBlank(userProfile)) {
            sb.append("\n\n## 用户画像（你对这位用户的了解）\n").append(userProfile);
        }

        // 层级4: 系统全局记忆（L4）
        if (StringUtils.isNotBlank(systemMemory)) {
            sb.append("\n\n## 系统全局记忆\n").append(systemMemory);
        }

        // 层级2c: 历史对话摘要
        if (StringUtils.isNotBlank(summary)) {
            sb.append("\n\n## 历史对话摘要\n> ").append(summary);
        }

        // 层级3: 子代理角色切换段
        if (subAgent != null && StringUtils.isNotBlank(subAgent.getAgentPrompt())) {
            sb.append("\n\n").append(subAgent.getAgentPrompt());
        }

        // 层级4: 查询结果注入
        if (queryResult != null) {
            sb.append("\n\n").append(queryResult.toPromptInjection());
        }

        return sb.toString();
    }
}
