package com.hyang.ich.omnitrix.infrastructure.vector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * 向量化服务 - 将文本转换为向量
 * 支持多种Embedding模型
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "omnitrix.vector.enabled", havingValue = "true")
public class EmbeddingService {

    private final VectorProperties properties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public EmbeddingService(VectorProperties properties) {
        this.properties = properties;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 将文本向量化
     */
    public float[] embed(String text) {
        try {
            VectorProperties.EmbeddingConfig config = properties.getEmbedding();
            
            // 构建请求
            String requestBody = String.format(
                "{\"input\": \"%s\", \"model\": \"%s\"}",
                text.replace("\"", "\\\""),
                config.getModel()
            );
            
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.set("Content-Type", "application/json");
            headers.set("Authorization", "Bearer " + config.getApiKey());
            
            org.springframework.http.HttpEntity<String> entity = 
                new org.springframework.http.HttpEntity<>(requestBody, headers);
            
            String url = config.getApiUrl();
            org.springframework.http.ResponseEntity<String> response = 
                restTemplate.postForEntity(url, entity, String.class);
            
            return parseEmbeddingResponse(response.getBody(), config.getDimension());
            
        } catch (Exception e) {
            log.error("向量化失败: {}", e.getMessage(), e);
            throw new RuntimeException("向量化失败: " + e.getMessage(), e);
        }
    }

    /**
     * 批量向量化
     */
    public List<float[]> embedBatch(List<String> texts) {
        List<float[]> results = new ArrayList<>();
        for (String text : texts) {
            results.add(embed(text));
        }
        return results;
    }

    /**
     * 解析Embedding响应
     */
    private float[] parseEmbeddingResponse(String responseBody, int dimension) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode data = root.get("data");
            
            if (data != null && data.isArray() && data.size() > 0) {
                JsonNode embedding = data.get(0).get("embedding");
                if (embedding != null) {
                    float[] result = new float[embedding.size()];
                    for (int i = 0; i < embedding.size(); i++) {
                        result[i] = (float) embedding.get(i).asDouble();
                    }
                    return result;
                }
            }
            
            // 如果解析失败，返回随机向量（用于测试）
            log.warn("无法解析Embedding响应，返回随机向量");
            return randomVector(dimension);
            
        } catch (Exception e) {
            log.error("解析Embedding响应失败: {}", e.getMessage());
            return randomVector(dimension);
        }
    }

    /**
     * 生成随机向量（用于测试/降级）
     */
    private float[] randomVector(int dimension) {
        float[] vector = new float[dimension];
        for (int i = 0; i < dimension; i++) {
            vector[i] = (float) (Math.random() * 2 - 1);
        }
        return vector;
    }

    /**
     * 获取向量维度
     */
    public int getDimension() {
        return properties.getEmbedding().getDimension();
    }
}
