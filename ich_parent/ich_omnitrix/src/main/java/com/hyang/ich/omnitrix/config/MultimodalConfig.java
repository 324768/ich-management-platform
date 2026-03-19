package com.hyang.ich.omnitrix.config;

import com.hyang.ich.omnitrix.infrastructure.llm.LlmProperties;
import com.hyang.ich.omnitrix.infrastructure.llm.MultimodalModel;
import com.hyang.ich.omnitrix.infrastructure.multimodal.MultimodalService;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 多模态配置
 * 当 omnitrix.multimodal.enabled=true 时生效
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "omnitrix.multimodal", name = "enabled", havingValue = "true")
public class MultimodalConfig {

    /**
     * 多模态模型 Bean
     */
    @Bean
    public MultimodalModel multimodalModel(LlmProperties properties) {
        return new MultimodalModel(properties);
    }

    /**
     * 多模态服务 Bean
     */
    @Bean
    public MultimodalService multimodalService(MultimodalModel multimodalModel) {
        return new MultimodalService(multimodalModel.getModel());
    }
}
