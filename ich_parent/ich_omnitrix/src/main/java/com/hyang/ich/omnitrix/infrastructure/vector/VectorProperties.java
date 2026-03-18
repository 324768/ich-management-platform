package com.hyang.ich.omnitrix.infrastructure.vector;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 向量数据库配置属性
 * 支持 Qdrant、Milvus 等主流向量数据库
 */
@Data
@Component
@ConfigurationProperties(prefix = "omnitrix.vector")
public class VectorProperties {

    /** 是否启用向量数据库 */
    private boolean enabled = false;

    /** 向量数据库类型: qdrant, milvus */
    private String provider = "qdrant";

    /** Qdrant 配置 */
    private QdrantConfig qdrant = new QdrantConfig();

    /** Milvus 配置 */
    private MilvusConfig milvus = new MilvusConfig();

    /** Embedding 模型配置 */
    private EmbeddingConfig embedding = new EmbeddingConfig();

    /** 是否已配置 */
    public boolean isConfigured() {
        return enabled && (provider.equalsIgnoreCase("qdrant") 
            || provider.equalsIgnoreCase("milvus"));
    }

    @Data
    public static class QdrantConfig {
        /** Qdrant 服务地址 */
        private String url = "http://localhost:6333";
        /** API Key (可选) */
        private String apiKey = "";
        /** Collection 名称 */
        private String collectionName = "ich-knowledge";
        /** 向量维度 */
        private int dimension = 1536;
        /** 距离度量方式: Cosine, Euclid, Dot */
        private String distance = "Cosine";
    }

    @Data
    public static class MilvusConfig {
        /** Milvus 服务地址 */
        private String host = "localhost";
        private int port = 19530;
        /** Collection 名称 */
        private String collectionName = "ich-knowledge";
        /** 向量维度 */
        private int dimension = 1536;
        /** 索引类型: IVF_FLAT, HNSW */
        private String indexType = "IVF_FLAT";
        /** 距离度量: L2, IP, COSINE */
        private String metricType = "L2";
    }

    @Data
    public static class EmbeddingConfig {
        /** Embedding 模型提供商: openai, siliconflow, dashscope */
        private String provider = "siliconflow";
        /** 模型名称 */
        private String model = "BAAI/bge-large-zh-v1.5";
        /** API URL */
        private String apiUrl = "https://api.siliconflow.cn/v1/embeddings";
        /** API Key */
        private String apiKey = "";
        /** 向量维度 */
        private int dimension = 1536;
        /** 最大输入长度 */
        private int maxInputLength = 512;
    }
}
