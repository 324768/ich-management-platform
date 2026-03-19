package com.hyang.ich.omnitrix.infrastructure.vector;

import dev.langchain4j.model.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.oneturn.OneturnEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 嵌入服务（Embedding Service）
 * 负责将文本转换为向量表示，用于向量检索
 */
@Slf4j
@Component
public class EmbeddingService {

    private final VectorProperties properties;
    private final EmbeddingModel embeddingModel;

    public EmbeddingService(VectorProperties properties) {
        this.properties = properties;
        this.embeddingModel = createEmbeddingModel();
    }

    /**
     * 创建 Embedding 模型
     */
    private EmbeddingModel createEmbeddingModel() {
        VectorProperties.EmbeddingConfig config = properties.getEmbedding();

        if (!config.isUseRemote()) {
            throw new UnsupportedOperationException("本地Embedding模型暂不支持，请使用远程API");
        }

        String apiKey = config.getApiKey();
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("Embedding API Key 未配置！请在配置文件中设置 omnitrix.vector.embedding.api-key");
        }

        log.info("初始化远程Embedding模型: model={}, dimension={}, apiUrl={}",
                config.getModel(), config.getDimension(), config.getApiUrl());

        return OpenAiEmbeddingModel.builder()
                .apiKey(apiKey)
                .modelName(config.getModel())
                .dimensions(config.getDimension())
                .baseUrl(config.getApiUrl())
                .build();
    }

    /**
     * 单文本嵌入
     *
     * @param text 待嵌入文本
     * @return 嵌入向量
     */
    public float[] embed(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new float[0];
        }

        try {
            Response<Embedding> response = embeddingModel.embed(text);
            return response.content().vector();
        } catch (Exception e) {
            log.error("文本嵌入失败: {}", e.getMessage(), e);
            return new float[0];
        }
    }

    /**
     * 批量文本嵌入
     *
     * @param texts 待嵌入文本列表
     * @return 嵌入向量列表
     */
    public List<float[]> embedAll(List<String> texts) {
        List<float[]> results = new ArrayList<>();

        if (texts == null || texts.isEmpty()) {
            return results;
        }

        for (String text : texts) {
            float[] vector = embed(text);
            if (vector.length > 0) {
                results.add(vector);
            }
        }

        return results;
    }

    /**
     * 获取向量维度
     */
    public int getDimension() {
        return properties.getEmbedding().getDimension();
    }

    /**
     * 获取 Embedding 模型名称
     */
    public String getModelName() {
        return properties.getEmbedding().getModel();
    }

    /**
     * 将文本列表转换为单个嵌入请求（LangChain4j格式）
     */
    public List<Embedding> embedForMemory(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return new ArrayList<>();
        }

        try {
            Response<List<Embedding>> response = embeddingModel.embedAll(texts);
            return response.content();
        } catch (Exception e) {
            log.error("批量文本嵌入失败: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }
}
