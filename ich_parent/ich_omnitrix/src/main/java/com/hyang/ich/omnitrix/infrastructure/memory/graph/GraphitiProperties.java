package com.hyang.ich.omnitrix.infrastructure.memory.graph;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Graphiti 时序知识图谱配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "omnitrix.graphiti")
public class GraphitiProperties {

    /** 是否启用 Graphiti */
    private boolean enabled = false;

    /** Neo4j 连接地址 */
    private String uri = "bolt://localhost:7687";

    /** 用户名 */
    private String username = "neo4j";

    /** 密码 */
    private String password = "password";

    /** 事实过期时间（天） */
    private int factTtlDays = 90;
}
