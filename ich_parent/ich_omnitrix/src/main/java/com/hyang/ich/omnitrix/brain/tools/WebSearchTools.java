package com.hyang.ich.omnitrix.brain.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
public class WebSearchTools {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Value("${omnitrix.search.enabled:false}")
    private boolean searchEnabled;
    
    @Value("${omnitrix.search.provider:bing}")
    private String provider;
    
    @Value("${omnitrix.search.bing-api-key:}")
    private String bingApiKey;
    
    @Value("${omnitrix.search.bing-endpoint:https://api.bing.microsoft.com/v7.0/search}")
    private String bingEndpoint;
    
    @Value("${omnitrix.search.google-api-key:}")
    private String googleApiKey;
    
    @Value("${omnitrix.search.google-cse-id:}")
    private String googleCseId;
    
    @Value("${omnitrix.search.max-results:10}")
    private int maxResults;

    @Tool("联网搜索最新网络信息。当用户询问实时数据、最新新闻、天气、股票、事件等需要实时信息的问题时使用。")
    public String webSearch(String query, Integer maxResults) {
        if (!searchEnabled) {
            return ToolResultWrapper.error("联网搜索功能未启用").withErrorHint().toXml();
        }
        
        if (query == null || query.trim().isEmpty()) {
            return ToolResultWrapper.error("搜索关键词不能为空").withErrorHint().toXml();
        }
        
        int results = maxResults != null && maxResults > 0 ? Math.min(maxResults, this.maxResults) : this.maxResults;
        
        try {
            log.info("执行联网搜索: provider={}, query={}", provider, query);
            
            String searchResult = "google".equalsIgnoreCase(provider) 
                ? searchWithGoogle(query, results) 
                : searchWithBing(query, results);
            
            return ToolResultWrapper.success("联网搜索完成", searchResult).toXml();
            
        } catch (Exception e) {
            log.error("联网搜索异常: {}", e.getMessage());
            return ToolResultWrapper.error("联网搜索失败: " + e.getMessage()).withErrorHint().toXml();
        }
    }

    private String searchWithBing(String query, int count) {
        if (bingApiKey == null || bingApiKey.trim().isEmpty()) {
            throw new IllegalStateException("Bing API Key 未配置");
        }
        
        String url = bingEndpoint + "?q=" + java.net.URLEncoder.encode(query) + "&count=" + count + "&mkt=zh-CN";
        
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.set("Ocp-Apim-Subscription-Key", bingApiKey);
        
        org.springframework.http.ResponseEntity<String> response = restTemplate.exchange(
            url, org.springframework.http.HttpMethod.GET, 
            new org.springframework.http.HttpEntity<>(headers), String.class);
        
        return parseBingResponse(response.getBody());
    }

    private String parseBingResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode results = root.path("webPages").path("value");
            if (results.isMissingNode()) return "未找到相关搜索结果";
            
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Math.min(results.size(), maxResults); i++) {
                JsonNode r = results.get(i);
                sb.append(i + 1).append(". ").append(r.path("name").asText()).append("\n");
                sb.append("   摘要: ").append(r.path("snippet").asText()).append("\n");
                sb.append("   链接: ").append(r.path("url").asText()).append("\n\n");
            }
            return sb.toString();
        } catch (Exception e) {
            return "搜索结果解析失败";
        }
    }

    private String searchWithGoogle(String query, int count) {
        if (googleApiKey == null || googleCseId == null) {
            throw new IllegalStateException("Google API Key 或 CSE ID 未配置");
        }
        
        String url = "https://www.googleapis.com/customsearch/v1?key=" + googleApiKey 
                   + "&cx=" + googleCseId + "&q=" + java.net.URLEncoder.encode(query) + "&num=" + count;
        
        org.springframework.http.ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
        return parseGoogleResponse(response.getBody());
    }

    private String parseGoogleResponse(String responseBody) {
        try {
            JsonNode items = objectMapper.readTree(responseBody).path("items");
            if (items.isMissingNode()) return "未找到相关搜索结果";
            
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Math.min(items.size(), maxResults); i++) {
                JsonNode r = items.get(i);
                sb.append(i + 1).append(". ").append(r.path("title").asText()).append("\n");
                sb.append("   摘要: ").append(r.path("snippet").asText()).append("\n");
                sb.append("   链接: ").append(r.path("link").asText()).append("\n\n");
            }
            return sb.toString();
        } catch (Exception e) {
            return "搜索结果解析失败";
        }
    }

    @Tool("查询联网搜索功能是否可用")
    public String getSearchStatus() {
        if (!searchEnabled) return "联网搜索功能未启用";
        return "提供商: " + provider + ", Bing: " + (bingApiKey != null ? "已配置" : "未配置") 
             + ", Google: " + (googleApiKey != null ? "已配置" : "未配置");
    }
}
