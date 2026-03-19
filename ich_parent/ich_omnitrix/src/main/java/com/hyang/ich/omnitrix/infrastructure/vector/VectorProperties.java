package com.hyang.ich.omnitrix.infrastructure.vector;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Qdrant 向量数据库配置属性
 *
 * 配置示例（application.yml）：
 *
 * omnitrix:
 *   vector:
 *     enabled: true
 *     host: localhost
 *     port: 6333
 *     grpc-port: 6334
 *     api-key: your-qdrant-api-key (可选)
 *     collection-name: ich_knowledge
 *     embedding:
 *       model: BAAI/bge-large-zh-v1.5
 *       dimension: 1024
 *     search:
 *       top-k: 5
 *       score-threshold: 0.7
 */
@Slf4j
@Data
@Component
@ConfigurationProperties(prefix = "omnitrix.vector")
public class VectorProperties {

    /** 是否启用向量检索功能 */
    private boolean enabled = false;

    /** Qdrant 服务地址（HTTP） */
    private String host = "localhost";

    /** Qdrant HTTP 端口 */
    private int port = 6333;

    /** Qdrant gRPC 端口 */
    private int grpcPort = 6334;

    /** Qdrant API Key（可选，Qdrant Cloud 需要） */
    private String apiKey;

    /** 默认集合名称 */
    private String collectionName = "ich_knowledge";

    /** Embedding 模型配置 */
    private EmbeddingConfig embedding = new EmbeddingConfig();

    /** 向量检索配置 */
    private SearchConfig search = new SearchConfig();

    @Data
    public static class EmbeddingConfig {
        /** Embedding 模型名称 */
        private String model = "BAAI/bge-large-zh-v1.5";

        /** 向量维度 */
        private int dimension = 1024;

        /** Embedding API URL（使用 SiliconFlow） */
        private String apiUrl = "https://api.siliconflow.cn/v1";

        /** Embedding API Key */
        private String apiKey;

        /** 是否使用远程模型（true=调用API，false=本地模型） */
        private boolean useRemote = true;
    }

    @Data
    public static class SearchConfig {
        /** 搜索返回结果数量 */
        private int topK = 5;

        /** 相似度阈值（0-1），低于此分数的结果将被过滤 */
        private Double scoreThreshold = 0.7;
    }

    public String getUrl() {
        return "http://" + host + ":" + port;
    }

    public boolean isConfigured() {
        return enabled && host != null && !host.isEmpty();
    }
}
