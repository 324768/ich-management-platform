# ICH 管理系统技术文档

## 1. 项目概述
ICH 管理系统是一个基于微服务架构的企业级应用，采用前后端分离的开发模式。项目使用 Java 作为主要后端语言，前端采用 Vue.js 框架，同时包含 Flutter 移动应用。

## 2. 技术栈

### 2.1 后端技术栈
- **核心框架**: Spring Boot 2.7.18
- **服务治理**: Apache Dubbo 2.7.8 (RPC框架)
- **ORM框架**: MyBatis Spring Boot Starter 2.2.2
- **数据库**: MySQL 8.0.33
- **构建工具**: Maven
- **JDK版本**: 1.8
- **工具类库**: Guava 31.1-jre

### 2.2 前端技术栈
- **管理后台**: Vue.js
- **用户端**: Vue.js
- **移动应用**: Flutter

## 3. 项目结构

### 3.1 项目根目录
```
ich-management-platform/                     # 【项目顶级目录】
│
├── ich_parent/                       # 【Maven 父工程目录】
│   ├── pom.xml                       # 父POM：统一版本管理、聚合所有子模块
│   │
│   ├── ich_common/                   # 1️⃣ 公共基础模块 (jar)
│   │   ├── src/main/java/
│   │   │   ├── constant/            # 常量定义
│   │   │   ├── enums/               # 枚举类
│   │   │   ├── exception/           # 自定义异常
│   │   │   ├── utils/               # 工具类
│   │   │   └── vo/                  # 通用VO对象/Result对象
│   │   └── pom.xml
│   │
│   ├── ich_interface/               # 2️⃣ Dubbo服务接口定义 (jar)
│   │   ├── src/main/java/
│   │   │   ├── user/                # 用户服务接口
│   │   │   │   ├── UserService.java
│   │   │   │   └── dto/             # 用户服务DTO
│   │   │   ├── content/             # 内容服务接口
│   │   │   │   ├── ContentService.java
│   │   │   │   └── dto/             # 内容服务DTO
│   │   │   └── omnitrix/            # 核心服务接口
│   │   │       ├── OmnitrixService.java
│   │   │       └── dto/             # 核心服务DTO
│   │   └── pom.xml
│   │
│   ├── ich_user_provider/           # 3️⃣ 用户服务提供者 (jar)
│   │   ├── src/main/java/
│   │   │   ├── UserProviderApplication.java  # 启动类
│   │   │   ├── service/impl/        # Dubbo服务实现 (UserServiceImpl)
│   │   │   ├── mapper/              # MyBatis Mapper接口
│   │   │   ├── entity/              # 数据库实体 (User, Role...)
│   │   │   └── config/              # 数据库、Dubbo Provider配置
│   │   ├── src/main/resources/
│   │   │   ├── application.yml      # Spring Boot/Dubbo配置
│   │   │   └── mapper/              # MyBatis XML映射文件
│   │   └── pom.xml
│   │
│   ├── ich_content_provider/        # 4️⃣ 内容服务提供者 (jar)
│   │   ├── src/main/java/
│   │   │   ├── ContentProviderApplication.java  # 启动类
│   │   │   ├── service/impl/        # Dubbo服务实现 (ContentServiceImpl)
│   │   │   ├── mapper/              # MyBatis Mapper接口
│   │   │   ├── entity/              # 数据库实体 (Content, Tag...)
│   │   │   └── config/              # 数据库、Dubbo Provider配置
│   │   ├── src/main/resources/
│   │   │   ├── application.yml
│   │   │   └── mapper/
│   │   └── pom.xml
│   │
│   ├── ich_omnitrix/                # 5️⃣ AI智能服务提供者 (jar)
│   │   ├── src/main/java/
│   │   │   ├── OmnitrixApplication.java  # 启动类
│   │   │   ├── service/impl/        # Dubbo服务实现
│   │   │   │   └── AIServiceImpl.java    # AI服务实现
│   │   │   ├── ai/                  # AI核心功能包
│   │   │   │   ├── AIClient.java    # AI客户端封装
│   │   │   │   ├── QAService.java   # 智能问答服务
│   │   │   │   └── Recommender.java # 推荐引擎
│   │   │   ├── mapper/              # MyBatis Mapper接口
│   │   │   ├── entity/              # 数据库实体
│   │   │   └── config/              # 配置类
│   │   ├── src/main/resources/
│   │   │   └── application.yml
│   │   └── pom.xml
│   │
│   ├── ich_user_web/                # 6️⃣ 用户端API接入层 (jar)
│   │   ├── src/main/java/
│   │   │   ├── UserWebApplication.java  # 启动类
│   │   │   ├── controller/         # RESTful API控制器
│   │   │   ├── interceptor/        # 拦截器
│   │   │   └── config/             # Web配置
│   │   ├── src/main/resources/
│   │   │   └── application.yml
│   │   └── pom.xml
│   │
│   └── ich_admin_web/               # 7️⃣ 管理端API接入层 (jar)
│       ├── src/main/java/
│       │   ├── AdminWebApplication.java  # 启动类
│       │   ├── controller/         # 管理端API控制器
│       │   ├── interceptor/        # 权限拦截器
│       │   └── config/             # Web配置
│       ├── src/main/resources/
│       │   └── application.yml
│       └── pom.xml
│
├── ich-user-web-vue/                # 8️⃣ 用户端前端应用 (Vue 3)
│   ├── src/
│   │   ├── views/                   # 页面组件
│   │   ├── components/              # 公共组件
│   │   ├── api/                     # API请求封装
│   │   ├── router/                  # 路由配置
│   │   └── store/                   # 状态管理
│   ├── public/
│   ├── package.json
│   └── vite.config.js
│
├── ich-admin-web-vue/               # 9️⃣ 管理端前端应用 (Vue 3)
│   ├── src/
│   │   ├── views/                   # 管理页面
│   │   ├── components/
│   │   ├── api/
│   │   ├── router/
│   │   └── store/
│   ├── package.json
│   └── vite.config.js
│
├── ich_user_app_flutter/            # 🔟 移动端应用 (Flutter)
│   ├── android/                     # Android原生项目
│   ├── ios/                         # iOS原生项目
│   ├── lib/                         # Dart代码目录
│   │   ├── main.dart                # 应用入口
│   │   ├── api/                     # API服务层
│   │   ├── model/                   # 数据模型
│   │   ├── pages/                   # UI页面
│   │   └── widgets/                 # 公共组件
│   └── pubspec.yaml                 # Flutter依赖配置
│
├── infra/                           # 1️⃣1️⃣ 基础设施配置
│   ├── docker-compose.yml           # 容器编排
│   ├── nginx/                       # Nginx配置
│   ├── mysql/                       # MySQL初始化脚本
│   └── scripts/                     # 部署脚本
│
├── docs/                            # 项目文档
│   └── TECHNICAL_DOCUMENTATION.md   # 技术文档
│
├── .gitignore                       # Git忽略配置
└── README.md                        # 项目说明文档
```

## 4. 模块详细说明

### 4.1 ich_common (公共模块)
- 包含项目通用工具类、常量、枚举、异常处理等
- 被所有其他模块依赖

### 4.2 ich_interface (接口定义)
- 定义Dubbo服务接口和DTO
- 被服务提供者和消费者共同依赖

### 4.3 ich_user_provider (用户服务)
- 用户认证与授权
- 用户信息管理
- 角色权限管理

### 4.4 ich_content_provider (内容服务)
- 内容管理
- 分类标签管理
- 内容审核

### 4.5 ich_omnitrix (AI智能服务)
- **智能问答系统**：基于NLP技术实现用户问题的智能理解与回答
- **推荐引擎**：根据用户历史行为和偏好提供个性化推荐
- **意图识别**：分析用户问题意图，提供精准回答
- **知识库管理**：维护和管理AI知识库，支持动态更新
- **对话管理**：维护对话上下文，实现多轮对话

### 4.6 ich_user_web (用户端API)
- 用户端接口
- 内容展示接口
- 移动端接口

### 4.7 ich_admin_web (管理端API)
- 系统管理接口
- 用户管理接口
- 内容管理接口
- 系统监控接口

## 5. 开发环境搭建

### 5.1 环境要求
- JDK 1.8+
- Maven 3.6+
- MySQL 8.0+
- Node.js 16+
- Flutter 3.0+

### 5.2 数据库初始化
```sql
# 创建数据库
CREATE DATABASE IF NOT EXISTS ich_user DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE DATABASE IF NOT EXISTS ich_content DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE DATABASE IF NOT EXISTS ich_omnitrix DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_general_ci;
```

### 5.3 启动顺序
1. 启动基础设施 (MySQL, Redis, Nacos, Zookeeper)
2. 启动服务提供者 (user_provider, content_provider, omnitrix)
3. 启动Web应用 (user_web, admin_web)
4. 启动前端应用 (user-web-vue, admin-web-vue)

## 6. 部署说明

### 6.1 容器化部署
```bash
# 构建镜像
docker-compose build

# 启动所有服务
docker-compose up -d
```

### 6.2 配置说明
- 应用配置: `application-{env}.yml`
- Dubbo注册中心: Nacos
- 配置中心: Nacos
- 服务监控: Spring Boot Admin

## 7. 开发规范

### 7.1 代码规范
- 遵循阿里巴巴Java开发手册
- 使用Lombok简化代码
- 统一使用4个空格缩进

### 7.2 分支管理
- `main`: 主分支，用于生产环境
- `develop`: 开发分支
- `feature/*`: 功能开发分支
- `bugfix/*`: 缺陷修复分支

## 8. AI功能说明

### 8.1 智能问答功能
- **功能描述**：系统能够理解自然语言问题，并从知识库中检索或生成合适的回答
- **技术实现**：
  - 使用NLP技术进行意图识别和实体提取
  - 基于向量数据库实现语义搜索
  - 支持上下文理解，实现多轮对话
  - 可配置的回答模板和知识库

### 8.2 推荐系统
- **功能描述**：根据用户行为和偏好提供个性化内容推荐
- **技术实现**：
  - 协同过滤算法
  - 内容相似度分析
  - 实时推荐和离线推荐结合

### 8.3 常见问题
1. 服务启动失败：检查端口是否被占用
2. 数据库连接失败：检查数据库配置
3. Dubbo服务注册失败：检查Nacos是否正常运行

### 8.2 相关文档
- [Spring Boot文档](https://spring.io/projects/spring-boot)
- [Dubbo文档](https://dubbo.apache.org/zh/)
- [Vue 3文档](https://v3.vuejs.org/)
- [Flutter文档](https://flutter.dev/docs)

---
文档最后更新日期：2025-11-16
