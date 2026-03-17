package com.hyang.ich.omnitrix.brain;

import com.hyang.ich.omnitrix.agent.tool.ToolRegistry;
import com.hyang.ich.omnitrix.infrastructure.memory.ChatMemoryManager;
import com.hyang.ich.omnitrix.infrastructure.skill.Skill;
import com.hyang.ich.omnitrix.infrastructure.skill.SkillPromptConfig;
import com.hyang.ich.omnitrix.service.UserMemoryService;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.service.AiServices;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * MasterBrain 工厂 — 根据角色（user/admin/ultra）动态构建 LangChain4j AiService 实例。
 * <p>
 * 核心职责：
 * 1. 从 ToolRegistry 获取角色对应的工具集
 * 2. 从 SkillPromptConfig 获取启用的 Skill Prompt
 * 3. 构建 ChatMemory（对话窗口）
 * 4. 组装 AiService 实例
 */
@Slf4j
@Component
public class MasterBrainFactory {

    private final ChatLanguageModel chatLanguageModel;
    private final ChatLanguageModel auxiliaryChatModel;
    private final StreamingChatLanguageModel streamingChatLanguageModel;
    private final ToolRegistry toolRegistry;
    private final SkillPromptConfig skillPromptConfig;
    private final UserMemoryService userMemoryService;
    private final ChatMemoryManager chatMemoryManager;

    public MasterBrainFactory(ChatLanguageModel chatLanguageModel,
                              @org.springframework.beans.factory.annotation.Qualifier("auxiliaryChatModel") ChatLanguageModel auxiliaryChatModel,
                              StreamingChatLanguageModel streamingChatLanguageModel,
                              ToolRegistry toolRegistry,
                              SkillPromptConfig skillPromptConfig,
                              UserMemoryService userMemoryService,
                              ChatMemoryManager chatMemoryManager) {
        this.chatLanguageModel = chatLanguageModel;
        this.auxiliaryChatModel = auxiliaryChatModel;
        this.streamingChatLanguageModel = streamingChatLanguageModel;
        this.toolRegistry = toolRegistry;
        this.skillPromptConfig = skillPromptConfig;
        this.userMemoryService = userMemoryService;
        this.chatMemoryManager = chatMemoryManager;
    }

    /**
     * 构建用户侧 MasterBrain（同步模式）
     */
    public UserMasterBrain buildUserBrain(Long userId, String sessionId) {
        List<Object> tools = toolRegistry.getToolsForRole("user");
        ChatMemory memory = buildMemory(sessionId);

        log.debug("构建 UserMasterBrain: userId={}, tools={}, sessionId={}",
                userId, tools.size(), sessionId);

        return AiServices.builder(UserMasterBrain.class)
                .chatLanguageModel(chatLanguageModel)
                .chatMemory(memory)
                .tools(tools)
                .build();
    }

    /**
     * 构建用户侧 MasterBrain（流式模式）
     */
    public UserMasterBrain buildUserBrainStreaming(Long userId, String sessionId) {
        List<Object> tools = toolRegistry.getToolsForRole("user");
        ChatMemory memory = buildMemory(sessionId);

        log.debug("构建 UserMasterBrain(streaming): userId={}, tools={}, sessionId={}",
                userId, tools.size(), sessionId);

        return AiServices.builder(UserMasterBrain.class)
                .streamingChatLanguageModel(streamingChatLanguageModel)
                .chatMemory(memory)
                .tools(tools)
                .build();
    }

    /**
     * 构建管理员侧 MasterBrain（同步模式）
     */
    public AdminMasterBrain buildAdminBrain(String sessionId) {
        List<Object> tools = toolRegistry.getToolsForRole("admin");
        ChatMemory memory = buildMemory(sessionId);

        log.debug("构建 AdminMasterBrain: tools={}, sessionId={}", tools.size(), sessionId);

        return AiServices.builder(AdminMasterBrain.class)
                .chatLanguageModel(chatLanguageModel)
                .chatMemory(memory)
                .tools(tools)
                .build();
    }

    /**
     * 构建管理员侧 MasterBrain（流式模式）
     */
    public AdminMasterBrain buildAdminBrainStreaming(String sessionId) {
        List<Object> tools = toolRegistry.getToolsForRole("admin");
        ChatMemory memory = buildMemory(sessionId);

        return AiServices.builder(AdminMasterBrain.class)
                .streamingChatLanguageModel(streamingChatLanguageModel)
                .chatMemory(memory)
                .tools(tools)
                .build();
    }

    /**
     * 构建 Ultra MasterBrain（同步模式）
     */
    public UltraMasterBrain buildUltraBrain(String sessionId) {
        List<Object> tools = toolRegistry.getToolsForRole("ultra");
        ChatMemory memory = buildMemory(sessionId);

        log.debug("构建 UltraMasterBrain: tools={}, sessionId={}", tools.size(), sessionId);

        return AiServices.builder(UltraMasterBrain.class)
                .chatLanguageModel(chatLanguageModel)
                .chatMemory(memory)
                .tools(tools)
                .build();
    }

    /**
     * 构建 Ultra MasterBrain（流式模式）
     */
    public UltraMasterBrain buildUltraBrainStreaming(String sessionId) {
        List<Object> tools = toolRegistry.getToolsForRole("ultra");
        ChatMemory memory = buildMemory(sessionId);

        return AiServices.builder(UltraMasterBrain.class)
                .streamingChatLanguageModel(streamingChatLanguageModel)
                .chatMemory(memory)
                .tools(tools)
                .build();
    }

    /**
     * 构建 Skill Prompt 文本 — 将所有启用的 Skill 的 systemPrompt 拼接注入 MasterBrain
     */
    public String buildSkillsPrompt() {
        List<Skill> enabledSkills = skillPromptConfig.getAllEnabledSkills();
        if (enabledSkills.isEmpty()) {
            return "";
        }
        return enabledSkills.stream()
                .map(Skill::getSystemPrompt)
                .collect(Collectors.joining("\n\n"));
    }

    /**
     * 构建用户画像 Prompt — 注入用户长期记忆
     */
    public String buildUserProfile(Long userId) {
        if (userId == null) return "";
        try {
            String profile = userMemoryService.buildUserProfile(userId);
            return profile != null ? profile : "";
        } catch (Exception e) {
            log.debug("构建用户画像异常: {}", e.getMessage());
            return "";
        }
    }

    // ========== 辅助模型降级构建 ==========

    public UserMasterBrain buildUserBrainFallback(Long userId, String sessionId) {
        List<Object> tools = toolRegistry.getToolsForRole("user");
        ChatMemory memory = buildMemory(sessionId);
        log.info("降级构建 UserMasterBrain(auxiliary): userId={}, sessionId={}", userId, sessionId);
        return AiServices.builder(UserMasterBrain.class)
                .chatLanguageModel(auxiliaryChatModel)
                .chatMemory(memory)
                .tools(tools)
                .build();
    }

    public AdminMasterBrain buildAdminBrainFallback(String sessionId) {
        List<Object> tools = toolRegistry.getToolsForRole("admin");
        ChatMemory memory = buildMemory(sessionId);
        log.info("降级构建 AdminMasterBrain(auxiliary): sessionId={}", sessionId);
        return AiServices.builder(AdminMasterBrain.class)
                .chatLanguageModel(auxiliaryChatModel)
                .chatMemory(memory)
                .tools(tools)
                .build();
    }

    public UltraMasterBrain buildUltraBrainFallback(String sessionId) {
        List<Object> tools = toolRegistry.getToolsForRole("ultra");
        ChatMemory memory = buildMemory(sessionId);
        log.info("降级构建 UltraMasterBrain(auxiliary): sessionId={}", sessionId);
        return AiServices.builder(UltraMasterBrain.class)
                .chatLanguageModel(auxiliaryChatModel)
                .chatMemory(memory)
                .tools(tools)
                .build();
    }

    public String getAuxiliaryModelName() {
        return "auxiliary";
    }

    /**
     * 构建对话记忆窗口 — 从 Redis/MySQL 预加载历史消息，解决多轮上下文丢失问题。
     */
    private ChatMemory buildMemory(String sessionId) {
        ChatMemory memory = MessageWindowChatMemory.builder()
                .id(sessionId)
                .maxMessages(20)
                .build();

        try {
            List<Map<String, String>> history = chatMemoryManager.loadHistory(sessionId);
            if (history != null && !history.isEmpty()) {
                // 如果有对话摘要（前几轮被压缩），注入为上下文
                String summary = chatMemoryManager.getSummary(sessionId);
                if (summary != null && !summary.isEmpty()) {
                    memory.add(UserMessage.from("[请基于之前的对话背景继续]"));
                    memory.add(dev.langchain4j.data.message.AiMessage.from(
                            "好的，我记得之前的对话：" + summary));
                }
                // 预加载历史消息
                for (Map<String, String> msg : history) {
                    String role = msg.get("role");
                    String content = msg.get("content");
                    if (content == null || content.isEmpty()) continue;
                    if ("user".equals(role)) {
                        memory.add(UserMessage.from(content));
                    } else if ("assistant".equals(role)) {
                        memory.add(dev.langchain4j.data.message.AiMessage.from(content));
                    }
                }
                log.debug("预加载对话历史到ChatMemory: sessionId={}, history={}, hasSummary={}",
                        sessionId, history.size(), summary != null && !summary.isEmpty());
            }
        } catch (Exception e) {
            log.warn("预加载对话历史失败，将使用空记忆: sessionId={}, error={}", sessionId, e.getMessage());
        }

        return memory;
    }
}
