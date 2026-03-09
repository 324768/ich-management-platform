package com.hyang.ich.omnitrix.infrastructure.llm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一消息列表构建工具，消除 LlmClient / LlmStreamHandler 中的重复代码。
 */
public final class LlmMessageBuilder {

    private LlmMessageBuilder() {}

    /**
     * 构建完整的 messages 列表：system + history + user
     */
    public static List<Map<String, String>> build(String systemPrompt,
                                                   List<Map<String, String>> history,
                                                   String userMessage) {
        List<Map<String, String>> messages = new ArrayList<>();

        Map<String, String> sysMsg = new HashMap<>();
        sysMsg.put("role", "system");
        sysMsg.put("content", systemPrompt);
        messages.add(sysMsg);

        if (history != null) {
            messages.addAll(history);
        }

        Map<String, String> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", userMessage);
        messages.add(userMsg);

        return messages;
    }

    /**
     * 计算消息列表中所有内容的字符总数（用于估算 token）
     */
    public static int totalCharCount(List<Map<String, String>> messages) {
        int count = 0;
        for (Map<String, String> msg : messages) {
            String content = msg.get("content");
            if (content != null) {
                count += content.length();
            }
        }
        return count;
    }
}
