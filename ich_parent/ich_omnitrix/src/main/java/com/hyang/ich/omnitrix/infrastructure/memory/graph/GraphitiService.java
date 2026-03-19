package com.hyang.ich.omnitrix.infrastructure.memory.graph;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Graphiti 时序知识图谱服务
 * 每条事实都有一个"有效时间窗口"，支持时序演化
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "omnitrix.graphiti", name = "enabled", havingValue = "true")
public class GraphitiService {

    private final Driver driver;
    private final GraphitiProperties properties;

    public GraphitiService(GraphitiProperties properties) {
        this.properties = properties;
        this.driver = GraphDatabase.driver(
                properties.getUri(),
                Auth.basic(properties.getUsername(), properties.getPassword())
        );
    }

    /**
     * 关闭驱动连接
     */
    @PreDestroy
    public void close() {
        if (driver != null) {
            try {
                driver.close();
                log.info("Neo4j 驱动已关闭");
            } catch (Exception e) {
                log.warn("关闭 Neo4j 驱动失败: {}", e.getMessage());
            }
        }
    }

    /**
     * 添加用户偏好事实（带时间戳）
     */
    public void addUserPreference(Long userId, String entity, String relation, String value) {
        String cypher = """
            MERGE (u:User {id: $userId})
            MERGE (e:Entity {name: $entity, type: $relation})
            SET e.value = $value, e.updatedAt = datetime()
            MERGE (u)-[r:PREFERS]->(e)
            SET r.value = $value, r.updatedAt = datetime()
            """;

        try (Session session = driver.session()) {
            session.run(cypher,
                    Values.parameters("userId", userId.toString(),
                            "entity", entity,
                            "relation", relation,
                            "value", value));
            log.debug("添加用户偏好: userId={}, entity={}, relation={}, value={}",
                    userId, entity, relation, value);
        } catch (Exception e) {
            log.error("添加用户偏好失败: {}", e.getMessage());
        }
    }

    /**
     * 获取用户当前偏好（最新）
     */
    public Map<String, String> getCurrentPreferences(Long userId) {
        String cypher = """
            MATCH (u:User {id: $userId})-[r:PREFERS]->(e:Entity)
            RETURN e.name as entity, e.value as value, e.updatedAt as updatedAt
            ORDER BY e.updatedAt DESC
            """;

        Map<String, String> prefs = new HashMap<>();
        try (Session session = driver.session()) {
            Result result = session.run(cypher,
                    Values.parameters("userId", userId.toString()));
            while (result.hasNext()) {
                Record record = result.next();
                String entity = record.get("entity").asString();
                String value = record.get("value").asString();
                prefs.put(entity, value);
            }
        } catch (Exception e) {
            log.error("获取用户偏好失败: {}", e.getMessage());
        }
        return prefs;
    }

    /**
     * 获取用户偏好历史（时序）
     */
    public List<PreferenceHistory> getPreferenceHistory(Long userId, String entityType) {
        String cypher = """
            MATCH (u:User {id: $userId})-[r:PREFERS]->(e:Entity {type: $entityType})
            RETURN e.name as entity, r.value as value, r.updatedAt as updatedAt
            ORDER BY r.updatedAt DESC
            """;

        List<PreferenceHistory> history = new ArrayList<>();
        try (Session session = driver.session()) {
            Result result = session.run(cypher,
                    Values.parameters("userId", userId.toString(), "entityType", entityType));
            while (result.hasNext()) {
                Record record = result.next();
                history.add(new PreferenceHistory(
                        record.get("entity").asString(),
                        record.get("value").asString(),
                        record.get("updatedAt").asLocalDateTime()
                ));
            }
        } catch (Exception e) {
            log.error("获取偏好历史失败: {}", e.getMessage());
        }
        return history;
    }

    /**
     * 清理过期事实
     */
    public void cleanExpiredFacts() {
        String cypher = """
            MATCH (e:Entity)
            WHERE e.updatedAt < datetime() - duration({days: $ttlDays})
            DETACH DELETE e
            """;

        try (Session session = driver.session()) {
            session.run(cypher,
                    Values.parameters("ttlDays", properties.getFactTtlDays()));
            log.info("清理过期事实完成");
        } catch (Exception e) {
            log.error("清理过期事实失败: {}", e.getMessage());
        }
    }

    public record PreferenceHistory(String entity, String value, LocalDateTime updatedAt) {}
}
