# 非遗智能服务中台 (ICH Omnitrix)

## 快速开始

```bash
# 启动服务
mvn spring-boot:run -Dspring-boot.run.profiles=local

# 或 IDE 中配置 VM options: -Dspring.profiles.active=local
```

访问: http://localhost:8083

## 可选功能

- **向量数据库**: 启动 Qdrant 后修改 `vector.enabled: true`
- **Nacos配置中心**: 修改 `spring.cloud.nacos.config.enabled: true`
- **Prometheus监控**: http://localhost:8083/actuator/prometheus
