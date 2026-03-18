package com.hyang.ich.omnitrix.infrastructure.vector;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Collections;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Qdrant 向量数据库客户端
 * 提供向量存储和检索功能
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "omnitrix.vector.enabled", havingValue = "true")
public class QdrantVectorStore {

    private final QdrantClient client;
    private final VectorProperties properties;

    public QdrantVectorStore(VectorProperties properties) {
        this.properties = properties;
        VectorProperties.QdrantConfig config = properties.getQdrant();
        
        QdrantGrpcClient.Builder builder = QdrantGrpcClient.newBuilder(config.getUrl());
        if (config.getApiKey() != null && !config.getApiKey().isEmpty()) {
            builder.withApiKey(config.getApiKey());
        }
        this.client = builder.build();
        
        log.info("✅ Qdrant 客户端初始化完成: {}", config.getUrl());
    }

    /**
     * 初始化 Collection
     */
    public void initCollection() {
        try {
            client.createCollectionAsync(
                properties.getQdrant().getCollectionName(),
                properties.getQdrant().getDimension(),
                Collections.Distance.valueOf(properties.getQdrant().getDistance().toUpperCase())
            ).get();
            log.info("✅ Qdrant Collection 创建完成: {}", properties.getQdrant().getCollectionName());
        } catch (Exception e) {
            log.warn("Collection 可能已存在: {}", e.getMessage());
        }
    }

    /**
     * 存储向量数据
     */
    public void upsert(String id, float[] vector, String text, Map<String, Object> payload) {
        try {
            client.upsertAsync(
                properties.getQdrant().getCollectionName(),
                id,
                vector,
                text,
                payload
            ).get();
        } catch (Exception e) {
            log.error("向量存储失败: {}", e.getMessage(), e);
            throw new RuntimeException("向量存储失败", e);
        }
    }

    /**
     * 相似度检索
     */
    public List<SearchResult> search(float[] queryVector, int topK, Map<String, Object> filters) {
        try {
            var results = client.searchAsync(
                properties.getQdrant().getCollectionName(),
                queryVector,
                topK,
                filters
            ).get();
            
            return results.stream()
                .map(r -> new SearchResult(r.getId(), r.getScore(), r.getText(), r.getPayload()))
                .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("向量检索失败: {}", e.getMessage(), e);
            throw new RuntimeException("向量检索失败", e);
        }
    }

    /**
     * 删除向量
     */
    public void delete(String id) {
        try {
            client.deleteAsync(
                properties.getQdrant().getCollectionName(),
                id
            ).get();
        } catch (Exception e) {
            log.error("向量删除失败: {}", e.getMessage(), e);
            throw new RuntimeException("向量删除失败", e);
        }
    }

    /**
     * 搜索结果
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class SearchResult {
        private String id;
        private double score;
        private String text;
        private Map<String, Object> payload;
    }
}
