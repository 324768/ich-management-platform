package com.hyang.ich.omnitrix.infrastructure.vector;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 向量检索增强RAG服务
 * 整合Embedding和向量数据库，提供语义搜索能力
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "omnitrix.vector.enabled", havingValue = "true")
public class VectorRagService {

    private final EmbeddingService embeddingService;
    private final QdrantVectorStore vectorStore;

    public VectorRagService(EmbeddingService embeddingService, QdrantVectorStore vectorStore) {
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
        
        // 初始化 Collection
        try {
            vectorStore.initCollection();
        } catch (Exception e) {
            log.warn("向量Collection初始化失败: {}", e.getMessage());
        }
    }

    /**
     * 语义搜索 - 将用户问题转换为向量并检索
     */
    public List<RagResult> semanticSearch(String query, int topK) {
        // 1. 将用户问题向量化
        float[] queryVector = embeddingService.embed(query);
        
        // 2. 向量检索
        List<QdrantVectorStore.SearchResult> results = 
            vectorStore.search(queryVector, topK, null);
        
        // 3. 转换为RAG结果
        return results.stream()
            .map(r -> new RagResult(
                r.getId(),
                r.getText(),
                r.getScore(),
                (String) r.getPayload().get("source"),
                (String) r.getPayload().get("title")
            ))
            .collect(Collectors.toList());
    }

    /**
     * 添加知识文档到向量库
     */
    public void addDocument(String id, String content, String title, String source) {
        float[] vector = embeddingService.embed(content);
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("title", title);
        payload.put("source", source);
        payload.put("content", content);
        
        vectorStore.upsert(id, vector, content, payload);
        
        log.info("✅ 文档已添加到向量库: id={}, title={}", id, title);
    }

    /**
     * 批量添加文档
     */
    public void addDocuments(List<Document> documents) {
        for (Document doc : documents) {
            addDocument(doc.getId(), doc.getContent(), doc.getTitle(), doc.getSource());
        }
        log.info("✅ 批量添加文档完成: {} 条", documents.size());
    }

    /**
     * 删除文档
     */
    public void deleteDocument(String id) {
        vectorStore.delete(id);
        log.info("✅ 文档已从向量库删除: id={}", id);
    }

    /**
     * 构建RAG上下文 - 将检索结果格式化为LLM输入
     */
    public String buildRagContext(String query, int topK) {
        List<RagResult> results = semanticSearch(query, topK);
        
        if (results.isEmpty()) {
            return "";
        }
        
        StringBuilder context = new StringBuilder();
        context.append("参考知识库内容:\n\n");
        
        for (int i = 0; i < results.size(); i++) {
            RagResult r = results.get(i);
            context.append(String.format("【%d】%s\n", i + 1, r.getTitle() != null ? r.getTitle() : ""));
            context.append(r.getText()).append("\n\n");
        }
        
        return context.toString();
    }

    @Data
    public static class RagResult {
        private String id;
        private String text;
        private double score;
        private String source;
        private String title;

        public RagResult(String id, String text, double score, String source, String title) {
            this.id = id;
            this.text = text;
            this.score = score;
            this.source = source;
            this.title = title;
        }
    }

    @Data
    public static class Document {
        private String id;
        private String content;
        private String title;
        private String source;
    }
}
