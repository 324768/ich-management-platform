package com.hyang.ich.omnitrix.infrastructure.memory;

import com.hyang.ich.omnitrix.infrastructure.llm.LlmClient;
import com.hyang.ich.omnitrix.infrastructure.llm.LlmResponse;
import com.hyang.ich.omnitrix.infrastructure.prompt.PromptManager;
import com.hyang.ich.omnitrix.mapper.AiConversationMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class MemorySummarizer {

    private final ChatMemoryManager memoryManager;
    private final LlmClient llmClient;
    private final PromptManager promptManager;
    private final AiConversationMapper conversationMapper;

    public MemorySummarizer(ChatMemoryManager memoryManager, LlmClient llmClient,
                            PromptManager promptManager, AiConversationMapper conversationMapper) {
        this.memoryManager = memoryManager;
        this.llmClient = llmClient;
        this.promptManager = promptManager;
        this.conversationMapper = conversationMapper;
    }

    /**
     * 异步检查并执行摘要压缩
     */
    @Async("aiAsyncExecutor")
    public void summarizeIfNeeded(String sessionId, Long conversationId) {
        try {
            if (!memoryManager.needsSummarization(sessionId)) {
                return;
            }

            List<Map<String, String>> toSummarize = memoryManager.getMessagesForSummarization(sessionId);
            if (toSummarize == null || toSummarize.isEmpty()) {
                return;
            }

            // 构建历史文本
            StringBuilder historyText = new StringBuilder();
            for (Map<String, String> msg : toSummarize) {
                String role = "user".equals(msg.get("role")) ? "用户" : "AI";
                historyText.append(role).append(": ").append(msg.get("content")).append("\n");
            }

            // 获取摘要 Prompt
            String summaryPrompt = promptManager.resolve("memory_summarizer") + historyText.toString();

            // 调用 LLM 生成摘要
            LlmResponse response = llmClient.chatAuxiliary(summaryPrompt, new ArrayList<>(), "请生成摘要");
            String summary = response.getContent();

            if (summary != null && !summary.isEmpty()) {
                // 压缩 Redis 中的消息
                memoryManager.applyCompression(sessionId, summary);

                // 更新数据库
                if (conversationId != null) {
                    conversationMapper.updateSummary(conversationId, summary);
                }

                log.info("摘要生成完成: sessionId={}, 摘要长度={}", sessionId, summary.length());
            }

        } catch (Exception e) {
            log.error("摘要压缩失败: sessionId={}, error={}", sessionId, e.getMessage(), e);
        }
    }
}
