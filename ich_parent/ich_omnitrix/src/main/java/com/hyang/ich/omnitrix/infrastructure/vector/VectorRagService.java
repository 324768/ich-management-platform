package com.hyang.ich.omnitrix.infrastructure.vector;

import com.hyang.ich.omnitrix.infrastructure.context.ContextCompressor;
import com.hyang.ich.omnitrix.infrastructure.context.ContextStrategy;
import com.hyang.ich.omnitrix.infrastructure.llm.LlmProperties;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * RAG（检索增强生成）服务
 * 结合向量检索和LLM生成，提供基于知识库的问答能力
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "omnitrix.vector", name = "enabled", havingValue = "true")
public class VectorRagService {

    private final QdrantVectorStore vectorStore;
    private final EmbeddingService embeddingService;
    private final ChatLanguageModel chatModel;
    private final VectorProperties properties;
    private final ContextCompressor contextCompressor;
    private final ContextStrategy contextStrategy;
    private final QueryRewriter queryRewriter;
    private final RelevanceEvaluator relevanceEvaluator;
    private final HallucinationChecker hallucinationChecker;

    public VectorRagService(QdrantVectorStore vectorStore,
                           EmbeddingService embeddingService,
                           VectorProperties properties,
                           LlmProperties llmProperties,
                           ContextCompressor contextCompressor,
                           ContextStrategy contextStrategy,
                           QueryRewriter queryRewriter,
                           RelevanceEvaluator relevanceEvaluator,
                           HallucinationChecker hallucinationChecker) {
        this.vectorStore = vectorStore;
        this.embeddingService = embeddingService;
        this.properties = properties;
        this.contextCompressor = contextCompressor;
        this.contextStrategy = contextStrategy;
        this.queryRewriter = queryRewriter;
        this.relevanceEvaluator = relevanceEvaluator;
        this.hallucinationChecker = hallucinationChecker;

        // 创建用于RAG的Chat模型（使用主模型配置）
        String apiKey = llmProperties.getPrimaryConfig().getApiKey();
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("LLM API Key 未配置！请在配置文件中设置 omnitrix.llm.api-key");
        }

        this.chatModel = OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(llmProperties.getPrimaryConfig().getModel())
                .baseUrl(llmProperties.getPrimaryConfig().getApiUrl())
                .temperature(0.3)
                .timeout(Duration.ofSeconds(60))
                .build();
    }

    /**
     * 添加知识库文档
     *
     * @param id       文档ID
     * @param text     文档内容
     * @param metadata 元数据（如标题、来源、类别等）
     */
    public void addDocument(String id, String text, Map<String, Object> metadata) {
        log.info("添加知识库文档: id={}, textLength={}", id, text.length());
        vectorStore.addDocument(id, text, metadata);
    }

    /**
     * 批量添加知识库文档
     *
     * @param documents 文档列表
     */
    public void addDocuments(List<QdrantVectorStore.Document> documents) {
        log.info("批量添加知识库文档: count={}", documents.size());
        vectorStore.addDocuments(documents);
    }

    /**
     * 检索增强生成（RAG问答）
     *
     * @param query 用户问题
     * @return RAG回答结果
     */
    public RagResult answer(String query) {
        return answer(query, null, null);
    }

    /**
     * 检索增强生成（RAG问答）- 可自定义参数
     *
     * @param query          用户问题
     * @param topK           返回结果数量（null使用默认配置）
     * @param scoreThreshold 相似度阈值（null使用默认配置）
     * @return RAG回答结果
     */
    public RagResult answer(String query, Integer topK, Double scoreThreshold) {
        if (query == null || query.trim().isEmpty()) {
            return RagResult.builder()
                    .answer("请提供有效的问题")
                    .sources(Collections.emptyList())
                    .build();
        }

        log.debug("RAG检索: query={}", query);

        // 1. 向量检索
        List<QdrantVectorStore.SearchResult> searchResults = vectorStore.search(query, topK, scoreThreshold);

        if (searchResults.isEmpty()) {
            log.debug("未找到相关知识库内容");
            return RagResult.builder()
                    .answer("抱歉，知识库中没有找到与您问题相关的内容。")
                    .sources(Collections.emptyList())
                    .build();
        }

        // 2. 构建上下文
        String context = buildContext(searchResults);

        // 3. 构建Prompt并调用LLM
        String prompt = buildPrompt(query, context);

        try {
            String answer = chatModel.chat(prompt);

            // 4. 构建结果
            List<Source> sources = searchResults.stream()
                    .map(r -> Source.builder()
                            .id(r.getId())
                            .text(r.getText())
                            .score(r.getScore())
                            .metadata(r.getMetadata())
                            .build())
                    .collect(Collectors.toList());

            log.info("RAG回答成功: query={}, sources={}", query, sources.size());

            return RagResult.builder()
                    .answer(answer)
                    .sources(sources)
                    .build();

        } catch (Exception e) {
            log.error("RAG生成失败: {}", e.getMessage(), e);
            return RagResult.builder()
                    .answer("抱歉，处理您的问题时出现错误。请稍后重试。")
                    .sources(Collections.emptyList())
                    .build();
        }
    }

    /**
     * 检索知识库（不生成回答）
     *
     * @param query 查询文本
     * @return 检索结果列表
     */
    public List<QdrantVectorStore.SearchResult> retrieve(String query) {
        return retrieve(query, null, null);
    }

    /**
     * 检索知识库（可自定义参数）
     */
    public List<QdrantVectorStore.SearchResult> retrieve(String query, Integer topK, Double scoreThreshold) {
        return vectorStore.search(query, topK, scoreThreshold);
    }

    /**
     * 删除知识库文档
     */
    public void deleteDocument(String id) {
        vectorStore.deleteDocument(id);
    }

    /**
     * 清空知识库
     */
    public void clearKnowledgeBase() {
        vectorStore.clearCollection();
    }

    /**
     * 获取知识库状态
     */
    public QdrantVectorStore.CollectionInfo getKnowledgeBaseStatus() {
        return vectorStore.getCollectionInfo();
    }

    /**
     * 构建检索上下文
     */
    private String buildContext(List<QdrantVectorStore.SearchResult> results) {
        StringBuilder context = new StringBuilder();
        context.append("以下是知识库中与问题相关的内容：\n\n");

        for (int i = 0; i < results.size(); i++) {
            QdrantVectorStore.SearchResult result = results.get(i);
            context.append("【内容 ").append(i + 1).append("】\n");
            context.append(result.getText()).append("\n\n");
        }

        return context.toString();
    }

    /**
     * 构建RAG Prompt
     */
    private String buildPrompt(String query, String context) {
        return String.format("""
                你是一个专业的知识问答助手。请根据以下知识库内容回答用户的问题。

                知识库内容：
                %s

                用户问题：%s

                要求：
                1. 仅根据知识库中的内容回答，不要编造信息
                2. 如果知识库中没有相关内容，请明确告知用户
                3. 回答要简洁、准确
                4. 如果有多个相关内容，可以综合回答

                回答：
                """, context, query);
    }

    // ========== 内部类 ==========

    @lombok.Data
    @lombok.Builder
    public static class Source {
        /** 文档ID */
        private String id;

        /** 文档内容 */
        private String text;

        /** 相似度分数 */
        private double score;

        /** 元数据 */
        private Map<String, Object> metadata;
    }

    @lombok.Data
    @lombok.Builder
    public static class RagResult {
        /** LLM生成的回答 */
        private String answer;

        /** 检索到的来源 */
        private List<Source> sources;
    }

    // ========== Agentic RAG（智能RAG）==========
    // 使用循环：检索 -> 评估相关性 -> 改写 -> 扩展 -> 最终生成

    private static final int MAX_ITERATIONS = 3;

    /**
     * Agentic RAG - 智能检索增强生成
     * 通过多轮循环：检索 -> 评估相关性 -> 改写查询 -> 扩展检索 -> 生成回答
     *
     * @param query 用户问题
     * @param language 目标语言
     * @return Agentic RAG 回答结果
     */
    public AgenticRagResult answerAgentic(String query, String language) {
        if (query == null || query.trim().isEmpty()) {
            return AgenticRagResult.builder()
                    .answer("请提供有效的问题")
                    .sources(Collections.emptyList())
                    .iterations(0)
                    .build();
        }

        log.info("Agentic RAG 开始: query={}", query);

        String currentQuery = query;
        List<QdrantVectorStore.SearchResult> allResults = new ArrayList<>();
        int iterations = 0;

        // 循环：检索 -> 评估 -> 改写
        while (iterations < MAX_ITERATIONS) {
            iterations++;
            log.debug("Agentic RAG 第{}轮: query={}", iterations, currentQuery);

            // 1. 检索
            List<QdrantVectorStore.SearchResult> results = vectorStore.search(
                    currentQuery,
                    properties.getSearch().getTopK(),
                    properties.getSearch().getScoreThreshold()
            );

            if (!results.isEmpty()) {
                allResults.addAll(results);
            }

            // 2. 评估相关性
            if (relevanceEvaluator.needsRewrite(currentQuery, results)) {
                // 改写查询
                currentQuery = queryRewriter.rewrite(currentQuery, language);
                log.debug("查询改写: -> {}", currentQuery);
                continue;
            }

            if (!relevanceEvaluator.needsExpansion(currentQuery, results)) {
                // 相关性足够，退出循环
                break;
            }

            // 3. 扩展检索（生成多个子查询）
            String expandedQuery = expandQuery(currentQuery);
            if (!expandedQuery.equals(currentQuery)) {
                currentQuery = expandedQuery;
                log.debug("查询扩展: -> {}", currentQuery);
            } else {
                break;
            }
        }

        // 去重
        Set<String> seenIds = new HashSet<>();
        List<QdrantVectorStore.SearchResult> deduplicatedResults = allResults.stream()
                .filter(r -> seenIds.add(r.getId()))
                .sorted(Comparator.comparingDouble(QdrantVectorStore.SearchResult::getScore).reversed())
                .limit(properties.getSearch().getTopK())
                .collect(Collectors.toList());

        if (deduplicatedResults.isEmpty()) {
            return AgenticRagResult.builder()
                    .answer("抱歉，知识库中没有找到与您问题相关的内容。")
                    .sources(Collections.emptyList())
                    .iterations(iterations)
                    .build();
        }

        // 4. 压缩上下文
        String context = deduplicatedResults.stream()
                .map(QdrantVectorStore.SearchResult::getText)
                .map(text -> contextCompressor.compress(text, "search_result"))
                .collect(Collectors.joining("\n\n"));

        // 5. 构建Prompt并生成回答
        String prompt = buildPrompt(query, context);
        String answer;
        try {
            answer = chatModel.chat(prompt);
        } catch (Exception e) {
            log.error("Agentic RAG 生成失败: {}", e.getMessage());
            answer = "抱歉，处理您的问题时出现错误。请稍后重试。";
        }

        // 6. 幻觉检测
        HallucinationChecker.HallucinationResult hallucinationResult =
                hallucinationChecker.check(answer, context);

        if (hallucinationResult.isHasHallucination()) {
            log.warn("检测到幻觉: {}", hallucinationResult.getFeedback());
        }

        // 7. 构建来源
        List<Source> sources = deduplicatedResults.stream()
                .map(r -> Source.builder()
                        .id(r.getId())
                        .text(r.getText())
                        .score(r.getScore())
                        .metadata(r.getMetadata())
                        .build())
                .collect(Collectors.toList());

        log.info("Agentic RAG 完成: iterations={}, sources={}, hallucination={}",
                iterations, sources.size(), hallucinationResult.isHasHallucination());

        return AgenticRagResult.builder()
                .answer(answer)
                .sources(sources)
                .iterations(iterations)
                .hallucinationDetected(hallucinationResult.isHasHallucination())
                .hallucinationFeedback(hallucinationResult.getFeedback())
                .build();
    }

    /**
     * 扩展查询（生成多个子查询）
     */
    private String expandQuery(String query) {
        String prompt = String.format("""
                请将以下查询分解为2-3个更具体的子查询。

                原始查询：%s

                要求：
                1. 每个子查询应该是原始查询的一个方面或具体问题
                2. 使用不同的关键词表达
                3. 用" OR "分隔每个子查询

                只返回子查询，用空格分隔，不要其他内容。
                """, query);

        try {
            return chatModel.chat(prompt).trim();
        } catch (Exception e) {
            log.warn("查询扩展失败: {}", e.getMessage());
            return query;
        }
    }

    // ========== Agentic RAG 结果类 ==========

    @lombok.Data
    @lombok.Builder
    public static class AgenticRagResult {
        /** LLM生成的回答 */
        private String answer;

        /** 检索到的来源 */
        private List<Source> sources;

        /** 迭代次数 */
        private int iterations;

        /** 是否检测到幻觉 */
        private boolean hallucinationDetected;

        /** 幻觉检测反馈 */
        private String hallucinationFeedback;
    }
}
