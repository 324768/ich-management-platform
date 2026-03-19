package com.hyang.ich.omnitrix.infrastructure.context;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 上下文压缩器 - 将长工具返回结果压缩后再注入上下文
 * 避免原样塞入导致上下文过长
 */
@Slf4j
@Component
public class ContextCompressor {

    private static final int MAX_ORIGINAL_LENGTH = 2000;
    private static final int COMPRESSED_LENGTH = 500;

    /**
     * 压缩工具返回结果
     * 如果结果超过阈值，保留关键信息并压缩
     */
    public String compress(String original, String type) {
        if (original == null || original.length() <= MAX_ORIGINAL_LENGTH) {
            return original;
        }

        return switch (type) {
            case "search_result" -> compressSearchResult(original);
            case "product_list" -> compressProductList(original);
            case "user_profile" -> compressUserProfile(original);
            default -> compressGeneric(original);
        };
    }

    private String compressSearchResult(String original) {
        String[] lines = original.split("\n");
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (String line : lines) {
            if (line.trim().isEmpty()) continue;
            if (count < 3) {
                sb.append(line).append("\n");
                count++;
            } else if (count < 10) {
                sb.append("[更多结果...] ").append(line, 0, Math.min(50, line.length())).append("\n");
                count++;
            }
        }
        return sb.toString();
    }

    private String compressProductList(String original) {
        return original.lines()
                .filter(line -> !line.isEmpty())
                .limit(10)
                .reduce((a, b) -> a + "\n" + b)
                .orElse(original);
    }

    private String compressUserProfile(String original) {
        String[] lines = original.split("\n");
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (String line : lines) {
            if (line.trim().isEmpty()) continue;
            if (count < 3) {
                sb.append(line).append("\n");
                count++;
            }
        }
        return sb.toString();
    }

    private String compressGeneric(String original) {
        if (original.length() <= MAX_ORIGINAL_LENGTH) return original;
        String head = original.substring(0, COMPRESSED_LENGTH);
        String tail = original.substring(original.length() - COMPRESSED_LENGTH);
        return head + "\n[...内容已压缩...]\n" + tail;
    }
}
