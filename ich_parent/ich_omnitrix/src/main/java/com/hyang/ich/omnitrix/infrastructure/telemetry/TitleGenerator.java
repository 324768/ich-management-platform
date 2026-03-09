package com.hyang.ich.omnitrix.infrastructure.telemetry;

import com.hyang.ich.omnitrix.infrastructure.llm.LlmClient;
import com.hyang.ich.omnitrix.infrastructure.llm.LlmResponse;
import com.hyang.ich.omnitrix.infrastructure.prompt.PromptTemplate;
import com.hyang.ich.omnitrix.service.ConversationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.ArrayList;

@Slf4j
@Component
public class TitleGenerator {

    private final LlmClient llmClient;
    private final ConversationService conversationService;

    public TitleGenerator(LlmClient llmClient, ConversationService conversationService) {
        this.llmClient = llmClient;
        this.conversationService = conversationService;
    }

    /**
     * 异步调用 LLM 生成对话标题，失败时回退到截取用户消息
     */
    @Async("aiAsyncExecutor")
    public void generateTitle(Long conversationId, String userMessage) {
        try {
            String prompt = String.format(PromptTemplate.TITLE_GENERATOR, userMessage);
            LlmResponse response = llmClient.chatAuxiliary(prompt, new ArrayList<>(), "请生成标题");
            String title = response.getContent();

            if (title != null && !title.isEmpty()) {
                // 清理多余引号和换行
                title = title.trim().replace("\"", "").replace("\n", "");
                if (title.length() > 20) {
                    title = title.substring(0, 20);
                }
                conversationService.updateTitle(conversationId, title);
                log.debug("LLM 生成标题: conversationId={}, title={}", conversationId, title);
                return;
            }
        } catch (Exception e) {
            log.warn("LLM 标题生成失败，使用回退策略: {}", e.getMessage());
        }

        // 回退: 截取用户消息
        String fallback = userMessage.length() > 20 ? userMessage.substring(0, 20) + "..." : userMessage;
        conversationService.updateTitle(conversationId, fallback);
    }
}
