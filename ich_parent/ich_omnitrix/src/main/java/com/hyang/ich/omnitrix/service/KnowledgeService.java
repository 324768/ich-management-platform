package com.hyang.ich.omnitrix.service;

import com.hyang.ich.omnitrix.entity.AiKnowledgeBase;
import com.hyang.ich.omnitrix.infrastructure.llm.LlmClient;
import com.hyang.ich.omnitrix.infrastructure.llm.LlmResponse;
import com.hyang.ich.omnitrix.mapper.AiKnowledgeBaseMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class KnowledgeService {

    private static final Set<String> STOP_WORDS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "的", "了", "是", "在", "有", "和", "就", "不", "人", "都",
            "一", "一个", "上", "也", "很", "到", "说", "要", "去", "你",
            "会", "着", "没有", "看", "好", "自己", "这", "他", "她", "它",
            "我", "什么", "吗", "吧", "呢", "啊", "哪", "怎么", "那",
            "请问", "请", "帮我", "可以", "能", "想", "知道", "告诉"
    )));

    private final AiKnowledgeBaseMapper knowledgeBaseMapper;
    private final LlmClient llmClient;

    public KnowledgeService(AiKnowledgeBaseMapper knowledgeBaseMapper, LlmClient llmClient) {
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.llmClient = llmClient;
    }

    /**
     * 按关键词搜索知识库（升级版：多token相关性匹配）
     */
    public List<AiKnowledgeBase> search(String keyword) {
        List<String> tokens = tokenize(keyword);
        if (tokens.isEmpty()) {
            return knowledgeBaseMapper.searchByKeyword(keyword);
        }
        try {
            List<AiKnowledgeBase> results = knowledgeBaseMapper.searchByTokens(tokens, 3);
            if (results != null && !results.isEmpty()) {
                log.debug("知识库多token搜索: tokens={}, 命中{}条", tokens, results.size());
                return results;
            }
        } catch (Exception e) {
            log.warn("多token搜索失败，回退单关键词: {}", e.getMessage());
        }
        return knowledgeBaseMapper.searchByKeyword(keyword);
    }

    /**
     * 检查是否有关键词命中知识库
     */
    public boolean hasMatch(String keyword) {
        List<AiKnowledgeBase> results = search(keyword);
        return results != null && !results.isEmpty();
    }

    /**
     * 简易中文分词：按标点/空格分割 + 双字/三字滑动窗口 + 去停用词
     */
    private List<String> tokenize(String text) {
        if (text == null || text.trim().isEmpty()) return Collections.emptyList();

        // Java 8 不支持 \p{IsGeneral_Category=xxx} 语法，改用直接列举Unicode码点
        // 标点符号: \p{Punct} + 中文标点 + 全角标点
        String cleaned = text.replaceAll("[\\p{Punct}\\s\uff0c\u3002\uff1f\uff01\u3001\uff1b\uff1a\u201c\u201d\u2018\u2019\u3010\u3011\uff08\uff09\u300a\u300b]+", " ").trim();

        List<String> tokens = new ArrayList<>();

        // 按空格分割得到词组
        String[] parts = cleaned.split("\\s+");
        for (String part : parts) {
            if (part.length() <= 1 || STOP_WORDS.contains(part)) continue;

            // 短词组直接加入
            if (part.length() <= 4) {
                tokens.add(part);
            } else {
                // 长词组用双字滑动窗口
                for (int i = 0; i < part.length() - 1; i++) {
                    String bigram = part.substring(i, Math.min(i + 2, part.length()));
                    if (!STOP_WORDS.contains(bigram)) {
                        tokens.add(bigram);
                    }
                }
            }
        }

        // 去重保留顺序
        return tokens.stream().distinct().limit(8).collect(Collectors.toList());
    }

    /**
     * RAG Reranking: 用辅助 LLM 对召回结果进行精排，只保留最相关的条目
     */
    public List<AiKnowledgeBase> searchAndRerank(String userQuery) {
        List<AiKnowledgeBase> candidates = search(userQuery);
        if (candidates == null || candidates.size() <= 1) {
            return candidates;
        }
        try {
            // 构建精排 Prompt
            StringBuilder sb = new StringBuilder();
            sb.append("用户问题: ").append(userQuery).append("\n\n");
            sb.append("候选知识库条目:\n");
            for (int i = 0; i < candidates.size(); i++) {
                AiKnowledgeBase kb = candidates.get(i);
                sb.append(i + 1).append(". Q: ").append(kb.getQuestion())
                        .append(" A: ").append(truncate(kb.getAnswer(), 80)).append("\n");
            }
            sb.append("\n请从以上候选中选出与用户问题最相关的条目编号（最多2个），用逗号分隔。");
            sb.append("\n只输出编号，不要输出其他内容。例如: 1,3");

            LlmResponse response = llmClient.chatAuxiliary(
                    "你是一个相关性判断器，只输出数字编号。", new ArrayList<>(), sb.toString());
            String content = response.getContent();

            if (content != null) {
                List<AiKnowledgeBase> reranked = new ArrayList<>();
                for (String numStr : content.trim().split("[,，\\s]+")) {
                    try {
                        int idx = Integer.parseInt(numStr.trim()) - 1;
                        if (idx >= 0 && idx < candidates.size()) {
                            reranked.add(candidates.get(idx));
                        }
                    } catch (NumberFormatException ignored) {}
                }
                if (!reranked.isEmpty()) {
                    log.debug("RAG Rerank: {}条→{}条", candidates.size(), reranked.size());
                    return reranked;
                }
            }
        } catch (Exception e) {
            log.warn("RAG Rerank 失败，返回原始结果: {}", e.getMessage());
        }
        return candidates;
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }

    /**
     * 按关键词搜索指定分类的知识库条目（供 DynamicSubAgent 使用）
     */
    public List<AiKnowledgeBase> searchByCategory(String keyword, Long categoryId) {
        if (categoryId == null) {
            return search(keyword);
        }
        try {
            List<AiKnowledgeBase> results = knowledgeBaseMapper.searchByKeywordAndCategory(keyword, categoryId);
            if (results != null && !results.isEmpty()) {
                log.debug("知识库分类搜索: keyword={}, categoryId={}, 命中{}条", keyword, categoryId, results.size());
                return results;
            }
        } catch (Exception e) {
            log.warn("知识库分类搜索失败，回退全局搜索: {}", e.getMessage());
        }
        return search(keyword);
    }

    /**
     * 增加命中次数
     */
    public void incrementHitCount(Long id) {
        knowledgeBaseMapper.incrementHitCount(id);
    }

    public List<AiKnowledgeBase> listAll() {
        return knowledgeBaseMapper.selectAll();
    }

    public List<AiKnowledgeBase> listPage(int pageNum, int pageSize, String keyword) {
        int offset = (pageNum - 1) * pageSize;
        return knowledgeBaseMapper.selectPage(offset, pageSize, keyword);
    }

    public int countAll(String keyword) {
        return knowledgeBaseMapper.countAll(keyword);
    }

    public void save(AiKnowledgeBase kb) {
        if (kb.getId() != null) {
            knowledgeBaseMapper.update(kb);
        } else {
            knowledgeBaseMapper.insert(kb);
        }
    }

    public void deleteById(Long id) {
        knowledgeBaseMapper.deleteById(id);
    }
}
