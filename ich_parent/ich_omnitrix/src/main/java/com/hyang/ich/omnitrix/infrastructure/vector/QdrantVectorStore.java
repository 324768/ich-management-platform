package com.hyang.ich.omnitrix.infrastructure.vector;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Collections;
import io.qdrant.client.grpc.Points;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Qdrant 向量存储服务
 * 提供向量数据的增删改查操作
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "omnitrix.vector", name = "enabled", havingValue = "true")
public class QdrantVectorStore {

    private final VectorProperties properties;
    private final EmbeddingService embeddingService;
    private QdrantClient qdrantClient;

    public QdrantVectorStore(VectorProperties properties, EmbeddingService embeddingService) {
        this.properties = properties;
        this.embeddingService = embeddingService;
    }

    /**
     * 初始化 Qdrant 客户端
     */
    @PostConstruct
    public void init() {
        if (!properties.isConfigured()) {
            log.warn("Qdrant 向量数据库未配置，跳过初始化");
            return;
        }

        try {
            QdrantGrpcClient.Builder builder = QdrantGrpcClient.newBuilder(
                    properties.getHost(),
                    properties.getGrpcPort(),
                    false
            );

            if (properties.getApiKey() != null && !properties.getApiKey().isEmpty()) {
                builder.withApiKey(properties.getApiKey());
            }

            qdrantClient = new QdrantClient(builder.build());
            log.info("Qdrant 客户端初始化成功: host={}, port={}",
                    properties.getHost(), properties.getGrpcPort());

            // 确保集合存在
            ensureCollection();

        } catch (Exception e) {
            log.error("Qdrant 客户端初始化失败: {}", e.getMessage(), e);
            throw new RuntimeException("Qdrant 初始化失败", e);
        }
    }

    /**
     * 确保集合存在，不存在则创建
     */
    private void ensureCollection() {
        try {
            String collectionName = properties.getCollectionName();

            // 检查集合是否存在
            boolean exists = qdrantClient.collectionExistsAsync(collectionName).join();

            if (!exists) {
                log.info("创建 Qdrant 集合: {}", collectionName);

                qdrantClient.createCollectionAsync(collectionName,
                        Collections.VectorParams.newBuilder()
                                .setDistance(Collections.Distance.Cosine)
                                .setSize(embeddingService.getDimension())
                                .build()
                ).join();

                log.info("Qdrant 集合创建成功: {}", collectionName);
            } else {
                log.debug("Qdrant 集合已存在: {}", collectionName);
            }
        } catch (Exception e) {
            log.error("确保集合存在失败: {}", e.getMessage(), e);
            throw new RuntimeException("Qdrant 集合初始化失败", e);
        }
    }

    /**
     * 添加文档到向量库
     *
     * @param id        文档唯一ID
     * @param text      文档文本内容
     * @param metadata  元数据（可包含标题、来源等）
     */
    public void addDocument(String id, String text, Map<String, Object> metadata) {
        if (qdrantClient == null) {
            throw new IllegalStateException("Qdrant 客户端未初始化");
        }

        try {
            float[] vector = embeddingService.embed(text);
            if (vector.length == 0) {
                log.warn("文本嵌入为空，跳过添加: id={}", id);
                return;
            }

            // 构建元数据
            Map<String, Object> docMetadata = new HashMap<>();
            docMetadata.put("text", text);
            docMetadata.put("content", text);
            if (metadata != null) {
                docMetadata.putAll(metadata);
            }

            Points.PointStruct point = Points.PointStruct.newBuilder()
                    .setId(Points.PointId.newBuilder().setUuid(id).build())
                    .setVectors(Points.Vectors.newBuilder()
                            .addVectors(Points.Vector.newBuilder()
                                    .addAllVector(Arrays.stream(vector).boxed().collect(Collectors.toList()))
                                    .build())
                            .build())
                    .putAllPayload(convertMetadataToPayload(docMetadata))
                    .build();

            qdrantClient.upsertAsync(
                    properties.getCollectionName(),
                    Collections.PointIdsList.newBuilder().addAllIds(Collections.PointId.newBuilder().setUuid(id).build()).build(),
                    Collections.ListFlatten
                ).join();

            // 使用正确的 API
            qdrantClient.upsertAsync(properties.getCollectionName(), List.of(point)).join();

            log.debug("文档添加到向量库成功: id={}, textLength={}", id, text.length());

        } catch (Exception e) {
            log.error("添加文档到向量库失败: id={}, error={}", id, e.getMessage(), e);
            throw new RuntimeException("添加文档失败", e);
        }
    }

    /**
     * 批量添加文档
     */
    public void addDocuments(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return;
        }

        List<Points.PointStruct> points = new ArrayList<>();

        for (Document doc : documents) {
            float[] vector = embeddingService.embed(doc.getText());
            if (vector.length == 0) {
                continue;
            }

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("text", doc.getText());
            metadata.put("content", doc.getText());
            if (doc.getMetadata() != null) {
                metadata.putAll(doc.getMetadata());
            }

            Points.PointStruct point = Points.PointStruct.newBuilder()
                    .setId(Points.PointId.newBuilder().setUuid(doc.getId()).build())
                    .setVectors(Points.Vectors.newBuilder()
                            .addVectors(Points.Vector.newBuilder()
                                    .addAllVector(Arrays.stream(vector).boxed().collect(Collectors.toList()))
                                    .build())
                            .build())
                    .putAllPayload(convertMetadataToPayload(metadata))
                    .build();

            points.add(point);
        }

        if (!points.isEmpty()) {
            try {
                qdrantClient.upsertAsync(properties.getCollectionName(), points).join();
                log.info("批量添加文档成功: count={}", points.size());
            } catch (Exception e) {
                log.error("批量添加文档失败: {}", e.getMessage(), e);
                throw new RuntimeException("批量添加文档失败", e);
            }
        }
    }

    /**
     * 向量相似度搜索
     *
     * @param query           查询文本
     * @param topK           返回结果数量
     * @param scoreThreshold  相似度阈值
     * @return 搜索结果列表
     */
    public List<SearchResult> search(String query, Integer topK, Double scoreThreshold) {
        if (qdrantClient == null) {
            throw new IllegalStateException("Qdrant 客户端未初始化");
        }

        try {
            float[] queryVector = embeddingService.embed(query);
            if (queryVector.length == 0) {
                log.warn("查询文本嵌入为空");
                return Collections.emptyList();
            }

            int limit = topK != null ? topK : properties.getSearch().getTopK();
            Double threshold = scoreThreshold != null ? scoreThreshold : properties.getSearch().getScoreThreshold();

            Points.SearchParams params = Points.SearchParams.newBuilder()
                    .setHnswEffort(1.0f)
                    .build();

            Points.SearchPoints searchRequest = Points.SearchPoints.newBuilder()
                    .setCollectionName(properties.getCollectionName())
                    .addAllVector(Arrays.stream(queryVector).boxed().collect(Collectors.toList()))
                    .setLimit(limit)
                    .setParams(params)
                    .setWithPayload(Points.WithPayloadSelector.newBuilder().setEnable(true).build())
                    .setWithVectors(Points.WithVectorsSelector.newBuilder().setEnable(true).build())
                    .build();

            if (threshold != null) {
                searchRequest = searchRequest.toBuilder()
                        .setScoreThreshold(threshold)
                        .build();
            }

            List<Points.ScoredPoint> results = qdrantClient.searchAsync(searchRequest).join();

            return results.stream()
                    .map(this::convertToSearchResult)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("向量搜索失败: query={}, error={}", query, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * 简单搜索（使用默认配置）
     */
    public List<SearchResult> search(String query) {
        return search(query, null, null);
    }

    /**
     * 删除文档
     */
    public void deleteDocument(String id) {
        if (qdrantClient == null) {
            throw new IllegalStateException("Qdrant 客户端未初始化");
        }

        try {
            qdrantClient.deleteAsync(
                    properties.getCollectionName(),
                    Points.Filter.newBuilder()
                            .addMust(Points.FieldCondition.newBuilder()
                                    .addKeys("id")
                                    .build())
                            .build(),
                    List.of(Points.PointId.newBuilder().setUuid(id).build())
            ).join();

            log.debug("删除文档成功: id={}", id);
        } catch (Exception e) {
            log.error("删除文档失败: id={}, error={}", id, e.getMessage(), e);
        }
    }

    /**
     * 清空集合
     */
    public void clearCollection() {
        if (qdrantClient == null) {
            throw new IllegalStateException("Qdrant 客户端未初始化");
        }

        try {
            qdrantClient.deleteAsync(
                    properties.getCollectionName(),
                    Points.Filter.newBuilder().build(),
                    null
            ).join();

            log.info("清空集合成功: {}", properties.getCollectionName());
        } catch (Exception e) {
            log.error("清空集合失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 获取集合信息
     */
    public CollectionInfo getCollectionInfo() {
        if (qdrantClient == null) {
            return null;
        }

        try {
            Collections.CollectionInfo info = qdrantClient.getCollectionInfoAsync(properties.getCollectionName()).join();
            return CollectionInfo.builder()
                    .name(properties.getCollectionName())
                    .vectorsCount(info.getVectorsCount())
                    .pointsCount(info.getPointsCount())
                    .status(info.getStatus().name())
                    .build();
        } catch (Exception e) {
            log.error("获取集合信息失败: {}", e.getMessage(), e);
            return null;
        }
    }

    // ========== 辅助方法 ==========

    private Map<String, io.qdrant.client.grpc.Value> convertMetadataToPayload(Map<String, Object> metadata) {
        Map<String, io.qdrant.client.grpc.Value> payload = new HashMap<>();

        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            Object value = entry.getValue();
            if (value == null) {
                continue;
            }

            io.qdrant.client.grpc.Value.Builder builder = io.qdrant.client.grpc.Value.newBuilder();

            if (value instanceof String) {
                builder.setStringValue((String) value);
            } else if (value instanceof Number) {
                builder.setDoubleValue(((Number) value).doubleValue());
            } else if (value instanceof Boolean) {
                builder.setBoolValue((Boolean) value);
            } else {
                builder.setStringValue(value.toString());
            }

            payload.put(entry.getKey(), builder.build());
        }

        return payload;
    }

    private SearchResult convertToSearchResult(Points.ScoredPoint point) {
        Map<String, Object> metadata = new HashMap<>();

        if (point.hasPayload()) {
            for (Map.Entry<String, io.qdrant.client.grpc.Value> entry : point.getPayloadMap().entrySet()) {
                io.qdrant.client.grpc.Value value = entry.getValue();
                if (value.hasStringValue()) {
                    metadata.put(entry.getKey(), value.getStringValue());
                } else if (value.hasDoubleValue()) {
                    metadata.put(entry.getKey(), value.getDoubleValue());
                } else if (value.hasBoolValue()) {
                    metadata.put(entry.getKey(), value.getBoolValue());
                }
            }
        }

        String text = metadata.get("text") != null ? metadata.get("text").toString() : "";

        return SearchResult.builder()
                .id(point.getId().getUuid())
                .text(text)
                .score(point.getScore())
                .metadata(metadata)
                .build();
    }

    // ========== 内部类 ==========

    @lombok.Data
    @lombok.Builder
    public static class Document {
        private String id;
        private String text;
        private Map<String, Object> metadata;
    }

    @lombok.Data
    @lombok.Builder
    public static class SearchResult {
        private String id;
        private String text;
        private double score;
        private Map<String, Object> metadata;
    }

    @lombok.Data
    @lombok.Builder
    public static class CollectionInfo {
        private String name;
        private long vectorsCount;
        private long pointsCount;
        private String status;
    }
}
