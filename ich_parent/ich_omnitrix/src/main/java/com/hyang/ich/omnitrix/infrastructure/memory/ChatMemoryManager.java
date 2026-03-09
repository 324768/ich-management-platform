package com.hyang.ich.omnitrix.infrastructure.memory;

import com.hyang.ich.omnitrix.entity.AiMessage;
import com.hyang.ich.omnitrix.infrastructure.llm.LlmProperties;
import com.hyang.ich.omnitrix.mapper.AiMessageMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
public class ChatMemoryManager {

    private static final int MAX_MESSAGES = 20;

    private final RedisChatMemoryStore redisStore;
    private final AiMessageMapper messageMapper;
    private final int maxContextTokens;

    public ChatMemoryManager(RedisChatMemoryStore redisStore, AiMessageMapper messageMapper,
                              LlmProperties llmProperties) {
        this.redisStore = redisStore;
        this.messageMapper = messageMapper;
        this.maxContextTokens = llmProperties.getMaxContextTokens();
    }

    /**
     * 加载对话历史：先查 Redis → 未命中查 MySQL → 写回 Redis
     */
    public List<Map<String, String>> loadHistory(String sessionId) {
        // 1. 优先从 Redis List 读取（appendMessage 写入的位置）
        List<Map<String, String>> cached = redisStore.getMessagesFromList(sessionId);
        if (cached != null && !cached.isEmpty()) {
            log.debug("从 Redis List 加载历史: sessionId={}, count={}", sessionId, cached.size());
            return trimByTokenLimit(cached);
        }

        // 2. 回退到旧的 JSON string key（兼容历史数据）
        cached = redisStore.getMessages(sessionId);
        if (cached != null && !cached.isEmpty()) {
            log.debug("从 Redis JSON 加载历史: sessionId={}, count={}", sessionId, cached.size());
            return cached;
        }

        // 3. Redis 未命中，查 MySQL
        List<AiMessage> dbMessages = messageMapper.selectBySessionId(sessionId);
        if (dbMessages == null || dbMessages.isEmpty()) {
            return new ArrayList<>();
        }

        // 4. 转换并写回 Redis
        List<Map<String, String>> history = new ArrayList<>();
        for (AiMessage msg : dbMessages) {
            if ("system".equals(msg.getRole())) continue;
            Map<String, String> m = new HashMap<>();
            m.put("role", msg.getRole());
            m.put("content", msg.getContent());
            history.add(m);
        }

        // 双重截断：先按消息数，再按 token 数
        if (history.size() > MAX_MESSAGES) {
            history = new ArrayList<>(history.subList(history.size() - MAX_MESSAGES, history.size()));
        }
        history = trimByTokenLimit(history);

        redisStore.replaceList(sessionId, history);
        log.debug("从 MySQL 加载历史并写入 Redis List: sessionId={}, count={}", sessionId, history.size());
        return history;
    }

    /**
     * 追加消息到记忆（Redis）
     */
    public void appendMessage(String sessionId, String role, String content) {
        redisStore.appendMessage(sessionId, role, content);
    }

    /**
     * 按 token 上限从最早的消息开始丢弃，确保不超过上下文窗口。
     * 估算规则：中文约 1 字 ≈ 0.7 token，英文约 1 word ≈ 1 token。
     */
    private List<Map<String, String>> trimByTokenLimit(List<Map<String, String>> messages) {
        int totalTokens = 0;
        for (Map<String, String> msg : messages) {
            totalTokens += estimateTokens(msg.get("content"));
        }
        if (totalTokens <= maxContextTokens) {
            return messages;
        }
        // 从最早的消息开始丢弃
        List<Map<String, String>> trimmed = new ArrayList<>(messages);
        while (totalTokens > maxContextTokens && trimmed.size() > 2) {
            Map<String, String> removed = trimmed.remove(0);
            totalTokens -= estimateTokens(removed.get("content"));
        }
        log.debug("Context Window 裁剪: {}条→{}条, 估算{}tokens", messages.size(), trimmed.size(), totalTokens);
        return trimmed;
    }

    /**
     * 粗估 token 数：中文字符 * 0.7 + 英文单词数
     */
    private int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        int chineseChars = 0;
        int asciiChars = 0;
        for (char c : text.toCharArray()) {
            if (c >= 0x4E00 && c <= 0x9FFF) {
                chineseChars++;
            } else if (c < 128) {
                asciiChars++;
            } else {
                chineseChars++; // 其他 Unicode 按中文估算
            }
        }
        return (int) (chineseChars * 0.7) + (asciiChars / 4) + 1;
    }

    /**
     * 获取对话摘要
     */
    public String getSummary(String sessionId) {
        return redisStore.getSummary(sessionId);
    }

    /**
     * 保存对话摘要
     */
    public void saveSummary(String sessionId, String summary) {
        redisStore.saveSummary(sessionId, summary);
    }

    /**
     * 检查是否需要摘要压缩
     */
    public boolean needsSummarization(String sessionId) {
        return redisStore.getMessageCount(sessionId) > MAX_MESSAGES;
    }

    /**
     * 执行摘要压缩：保留最近 6 条，前面的压缩为摘要
     */
    public List<Map<String, String>> getMessagesForSummarization(String sessionId) {
        List<Map<String, String>> messages = redisStore.getMessagesFromList(sessionId);
        if (messages == null || messages.isEmpty()) {
            messages = redisStore.getMessages(sessionId);
        }
        if (messages == null || messages.size() <= MAX_MESSAGES) {
            return null;
        }
        // 取前 N-6 条用于摘要
        int keepRecent = 6;
        int summarizeCount = messages.size() - keepRecent;
        return new ArrayList<>(messages.subList(0, summarizeCount));
    }

    /**
     * 压缩完成后，替换 Redis 中的消息列表
     */
    public void applyCompression(String sessionId, String summary) {
        List<Map<String, String>> messages = redisStore.getMessagesFromList(sessionId);
        if (messages == null || messages.isEmpty()) {
            messages = redisStore.getMessages(sessionId);
        }
        if (messages == null) return;

        int keepRecent = 6;
        if (messages.size() <= keepRecent) return;

        // 保留最近 6 条
        List<Map<String, String>> recent = new ArrayList<>(
                messages.subList(messages.size() - keepRecent, messages.size()));

        redisStore.replaceList(sessionId, recent);
        redisStore.saveSummary(sessionId, summary);
        log.info("记忆压缩完成: sessionId={}, 压缩前={}, 压缩后={}", sessionId, messages.size(), recent.size());
    }
}
