# Agent 跨租户导出/导入工具 - 完整技术方案

## 一、需求概述

### 1.1 背景描述

当前系统中，Agent（DynamicSubAgent）与知识库（Knowledge Base）的绑定关系是基于租户（Tenant）的。一个租户配置好的 Agent + 知识库，无法直接共享给其他租户使用。

**业务场景**：
- 租户 A 已配置好"法律咨询助手"Agent，绑定了包含 9 万份法律文书的知识库
- 租户 B、C、D 需要使用该 Agent 进行演示
- 期望：以压缩包形式导出 Agent + 知识库，其他租户导入后可直接使用

### 1.2 需求目标

开发一个轻量级的开发者工具，支持：
1. **导出功能**：将指定 Agent 及其绑定的知识库导出为压缩包
2. **导入功能**：将压缩包导入到目标租户，创建新的 Agent 和知识库

### 1.3 使用特点

- **临时性**：仅用于演示，不是长期功能
- **简单性**：不需要复杂的 Admin 管理界面
- **可分发**：以压缩包形式传递，无需数据库直接访问

---

## 二、技术架构

### 2.1 整体架构图

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                                     源租户                                          │
├─────────────────────────────────────────────────────────────────────────────────────┤
│  ┌─────────────────────┐                      ┌─────────────────────┐           │
│  │   Java 服务端       │                      │   Python 服务端      │           │
│  │                     │   1. 导出 Agent 配置  │                     │           │
│  │  sys_ai_agent 表    │ ────────────────────▶│  向量数据库          │           │
│  │  sys_ai_library 表  │    (查询+序列化)      │  (Milvus/Qdrant)    │           │
│  └─────────────────────┘                      └──────────┬──────────┘           │
│           │                                                    │                    │
│           │ 2. 调用 Python API                                  │                    │
│           │────────────────────────────────────────────────────┼───▶ 导出向量数据   │
│           │                                                    │                    │
│           ▼                                                    ▼                    │
│  ┌─────────────────────────────────────────────────────────────────────┐          │
│  │                         生成 ZIP 压缩包                              │          │
│  │    legal-agent-20260318.zip (包含 Agent 配置 + 向量数据)          │          │
│  └─────────────────────────────────────────────────────────────────────┘          │
└─────────────────────────────────────────────────────────────────────────────────────┘
                                        │
                                        │ 复制/分发 ZIP 文件
                                        ▼
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                                     目标租户                                         │
├─────────────────────────────────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────────────────────────────────────────────┐          │
│  │                         上传 ZIP 压缩包                             │          │
│  └─────────────────────────────────────────────────────────────────────┘          │
│                    │                                                       │
│                    ▼                                                       │
│  ┌─────────────────────┐                      ┌─────────────────────┐           │
│  │   Java 服务端       │                      │   Python 服务端      │           │
│  │                     │   1. 解析压缩包      │                     │           │
│  │  解析 JSON 配置     │ ◀─────────────────── │                     │           │
│  │  写入 sys_ai_agent │   2. 写入数据库       │                     │           │
│  │  写入 sys_ai_library│ ────────────────────▶│                     │           │
│  └─────────────────────┘    (新生成 ID)       └──────────┬──────────┘           │
│           │                                                    │                    │
│           │ 3. 调用 Python API                                  │                    │
│           │────────────────────────────────────────────────────┼───▶ 导入向量数据   │
│           │                                                    │                    │
│           ▼                                                    ▼                    │
│  ┌─────────────────────────────────────────────────────────────────────┐          │
│  │                         完成！Agent 可用                            │          │
│  └─────────────────────────────────────────────────────────────────────┘          │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 技术栈

| 层级 | 技术选型 | 说明 |
|------|---------|------|
| 后端框架 | Spring Boot | 复用现有 kortex-ai 模块 |
| 文件压缩 | Java ZIP API | `ZipInputStream` / `ZipOutputStream` |
| 数据库 | MyBatis-Plus | 复用现有 sys_ai_agent、dc_library 表 |
| 远程调用 | HTTP (RestTemplate) | 调用 Python 服务 API |
| 配置管理 | Spring `@Value` | 读取 Python 服务地址 |

---

## 三、数据模型

### 3.1 涉及的数据库表

#### 3.1.1 sys_ai_agent 表（Agent 配置）

```java
// 实体类位置: kortex-system/src/main/java/com/kortex/system/domain/SysAiAgent.java

@TableName("sys_ai_agent")
public class SysAiAgent extends TenantEntity {
    
    @TableId(value = "agent_id", type = IdType.ASSIGN_ID)
    private Long agentId;           // Agent ID (新生成)
    
    private String agentCode;        // Agent 编码 (唯一标识) - 导出时保留，导入时可修改
    private String agentName;        // Agent 名称
    private String icon;             // 图标
    private String description;      // 描述
    private String systemPrompt;     // 系统提示词
    private String routingKeywords; // 路由关键词 JSON
    private String toolsEnabled;    // 启用的工具列表 JSON
    private String fallbackStrategy; // 降级策略
    private Integer sort;           // 排序号
    private String status;           // 状态 0=正常 1=停用
    private String kbLibraryId;      // 知识库文库ID (新生成)
    private String permittedDeptIds; // 允许使用的部门 ID 列表
    private String permittedRoleKeys; // 允许使用的角色标识列表
    private Long version;            // 版本号
}
```

#### 3.1.2 dc_library 表（知识库）

```java
// 实体类位置: kortex-document/src/main/java/com/kortex/document/domain/DcLibrary.java

@TableName("dc_library")
public class DcLibrary extends TenantEntity {
    
    @TableId(value = "id")
    private String id;               // 知识库 ID (新生成)
    private String name;             // 知识库名称
    private String type;             // 类型 (固定为 knowledge)
    private String coverImage;      // 封面图片 URL
    private String description;      // 描述
    private Long size;               // 文档大小
    private String bucketName;       // 桶名称
    private String filePath;         // 文件存储路径
    private Long orderId;            // 排序
    private Long ownerId;            // 所有者 ID
    private String editingBy;        // 编辑中用户
    private String remindSwitch;     // 查看提醒开关
}
```

### 3.2 压缩包结构设计

```
legal-agent-20260318.zip
├── manifest.json                    # 清单文件（元信息）
├── agents/                          # Agent 配置目录
│   └── {index}.json                 # Agent 配置 JSON
│
└── knowledge/                       # 知识库数据（Python 端）
    ├── library.json                 # 知识库元信息
    └── vectors/                     # 向量数据目录
        └── vectors.json             # 向量数据 (Milvus/Qdrant 导出格式)
```

#### 3.2.1 manifest.json（清单文件）

```json
{
  "version": "1.0",
  "exportTime": "2026-03-18T10:30:00Z",
  "sourceTenantId": "tenant-a",
  "sourceTenantName": "租户A",
  "agentCount": 1,
  "checksum": "md5:abc123...",
  "createdBy": "admin",
  "description": "法律咨询助手 - 用于演示"
}
```

#### 3.2.2 agents/{index}.json（Agent 配置）

```json
{
  "agentCode": "legal_assistant",
  "agentName": "法律咨询助手",
  "icon": "law-icon",
  "description": "专门用于法律咨询的 AI 助手",
  "systemPrompt": "你是一个专业的法律咨询助手...",
  "routingKeywords": "[\"法律\", \"咨询\", \"纠纷\", \"案件\"]",
  "toolsEnabled": "[\"knowledge_search\", \"document_read\"]",
  "fallbackStrategy": "direct_reply",
  "sort": 1,
  "status": "0",
  "kbLibraryId": "lib_legal_001",
  "kbLibraryName": "法律文书库",
  "fileCount": 90000,
  "permittedDeptIds": "",
  "permittedRoleKeys": ""
}
```

#### 3.2.3 knowledge/library.json（知识库元信息）

```json
{
  "libraryId": "lib_legal_001",
  "libraryName": "法律文书库",
  "libraryType": "knowledge",
  "description": "包含各类法律文书模板和范本",
  "fileCount": 90000,
  "totalSize": 5368709120,
  "bucketName": "kortex-document",
  "filePath": "legal-docs/",
  "ownerId": 1
}
```

#### 3.2.4 knowledge/vectors/vectors.json（向量数据）

```json
{
  "vectorDbType": "milvus",
  "collectionName": "legal_assistant",
  "dimension": 1536,
  "metricType": "COSINE",
  "totalVectors": 90000,
  "vectors": [
    {
      "id": "vec_001",
      "text": "合同编号：2024-001\n甲方：...",
      "filepath": "/contracts/2024/001.pdf",
      "metadata": {
        "title": "劳动合同模板",
        "category": "合同",
        "createdAt": "2024-01-01"
      },
      "vector": [0.123, -0.456, 0.789, ...]
    }
  ]
}
```

---

## 四、核心代码设计

### 4.1 目录结构

```
kortex-modules/kortex-ai/src/main/java/com/kortex/ai/features/agent/tools/
├── controller/
│   └── AgentToolController.java          # 导出/导入 HTTP 接口
├── service/
│   ├── AgentExportService.java           # 导出业务逻辑
│   ├── AgentImportService.java           # 导入业务逻辑
│   └── impl/
│       ├── AgentExportServiceImpl.java
│       └── AgentImportServiceImpl.java
├── dto/
│   ├── AgentExportRequest.java            # 导出请求 DTO
│   ├── AgentExportDto.java                # 导出结果 DTO
│   ├── AgentImportRequest.java            # 导入请求 DTO
│   ├── AgentImportDto.java                # 导入结果 DTO
│   ├── ManifestDto.java                   # 清单文件 DTO
│   ├── AgentConfigDto.java                # Agent 配置 DTO
│   └── LibraryConfigDto.java              # 知识库配置 DTO
├── util/
│   └── AgentPackUtil.java                 # 压缩包工具类
└── remote/
    └── PythonKnowledgeService.java        # Python 服务调用接口
```

### 4.2 核心类设计

#### 4.2.1 清单文件 DTO (ManifestDto.java)

```java
package com.kortex.ai.features.agent.tools.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 导出包清单文件 DTO
 */
@Data
public class ManifestDto {
    
    /**
     * 包版本
     */
    private String version;
    
    /**
     * 导出时间
     */
    private LocalDateTime exportTime;
    
    /**
     * 源租户 ID
     */
    private String sourceTenantId;
    
    /**
     * 源租户名称
     */
    private String sourceTenantName;
    
    /**
     * Agent 数量
     */
    private Integer agentCount;
    
    /**
     * 文件校验和 (MD5)
     */
    private String checksum;
    
    /**
     * 导出人
     */
    private String createdBy;
    
    /**
     * 描述
     */
    private String description;
    
    /**
     * 包含的 Agent 列表
     */
    private List<AgentSummaryDto> agents;
    
    @Data
    public static class AgentSummaryDto {
        private String agentCode;
        private String agentName;
        private String libraryName;
        private Integer fileCount;
    }
}
```

#### 4.2.2 Agent 配置 DTO (AgentConfigDto.java)

```java
package com.kortex.ai.features.agent.tools.dto;

import lombok.Data;

/**
 * Agent 配置 DTO - 用于导出/导入
 */
@Data
public class AgentConfigDto {
    
    /**
     * Agent 编码 (唯一标识)
     */
    private String agentCode;
    
    /**
     * Agent 名称
     */
    private String agentName;
    
    /**
     * 图标
     */
    private String icon;
    
    /**
     * 描述
     */
    private String description;
    
    /**
     * 系统提示词
     */
    private String systemPrompt;
    
    /**
     * 路由关键词 JSON
     */
    private String routingKeywords;
    
    /**
     * 启用的工具列表 JSON
     */
    private String toolsEnabled;
    
    /**
     * 降级策略
     */
    private String fallbackStrategy;
    
    /**
     * 排序号
     */
    private Integer sort;
    
    /**
     * 状态
     */
    private String status;
    
    /**
     * 原知识库 ID (导出时)
     */
    private String kbLibraryId;
    
    /**
     * 知识库名称
     */
    private String kbLibraryName;
    
    /**
     * 文件数量
     */
    private Integer fileCount;
    
    /**
     * 允许使用的部门 ID 列表
     */
    private String permittedDeptIds;
    
    /**
     * 允许使用的角色标识列表
     */
    private String permittedRoleKeys;
}
```

#### 4.2.3 知识库配置 DTO (LibraryConfigDto.java)

```java
package com.kortex.ai.features.agent.tools.dto;

import lombok.Data;

/**
 * 知识库配置 DTO
 */
@Data
public class LibraryConfigDto {
    
    /**
     * 知识库 ID (导出时保留原值，导入时生成新值)
     */
    private String libraryId;
    
    /**
     * 知识库名称
     */
    private String libraryName;
    
    /**
     * 类型 (knowledge)
     */
    private String libraryType;
    
    /**
     * 描述
     */
    private String description;
    
    /**
     * 文件数量
     */
    private Integer fileCount;
    
    /**
     * 总大小 (字节)
     */
    private Long totalSize;
    
    /**
     * 桶名称
     */
    private String bucketName;
    
    /**
     * 文件存储路径
     */
    private String filePath;
    
    /**
     * 所有者 ID
     */
    private Long ownerId;
    
    /**
     * 向量数据库类型 (milvus/qdrant)
     */
    private String vectorDbType;
    
    /**
     * Collection 名称
     */
    private String collectionName;
    
    /**
     * 向量维度
     */
    private Integer dimension;
    
    /**
     * 向量数量
     */
    private Long vectorCount;
}
```

### 4.3 导出服务接口 (AgentExportService.java)

```java
package com.kortex.ai.features.agent.tools.service;

import com.kortex.ai.features.agent.tools.dto.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Agent 导出服务接口
 */
public interface AgentExportService {
    
    /**
     * 获取可导出的 Agent 列表
     */
    List<AgentConfigDto> listExportableAgents();
    
    /**
     * 导出 Agent 为 ZIP 文件
     * 
     * @param agentIds 要导出的 Agent ID 列表
     * @param includeVectors 是否包含向量数据
     * @param includeFiles 是否包含源文件
     * @return 导出的 ZIP 文件字节数组
     */
    byte[] exportAgents(List<Long> agentIds, boolean includeVectors, boolean includeFiles);
    
    /**
     * 导出单个 Agent
     * 
     * @param agentId Agent ID
     * @param includeVectors 是否包含向量数据
     * @return 导出的 ZIP 文件字节数组
     */
    byte[] exportAgent(Long agentId, boolean includeVectors);
}
```

### 4.4 导入服务接口 (AgentImportService.java)

```java
package com.kortex.ai.features.agent.tools.service;

import com.kortex.ai.features.agent.tools.dto.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Agent 导入服务接口
 */
public interface AgentImportService {
    
    /**
     * 预览导入文件
     * 
     * @param file 导入的 ZIP 文件
     * @return 预览信息
     */
    ManifestDto previewImport(MultipartFile file);
    
    /**
     * 执行导入
     * 
     * @param file 导入的 ZIP 文件
     * @param agentCodeMapping Agent 编码映射 (可选，为空则使用原编码)
     * @param libraryIdMapping 知识库 ID 映射 (可选，为空则生成新 ID)
     * @return 导入结果
     */
    AgentImportResultDto importAgents(MultipartFile file, 
                                       java.util.Map<String, String> agentCodeMapping,
                                       java.util.Map<String, String> libraryIdMapping);
    
    /**
     * 导入 Agent 配置 (不含向量数据)
     * 
     * @param agents Agent 配置列表
     * @return 导入结果
     */
    AgentImportResultDto importAgentConfigs(List<AgentConfigDto> agents);
}
```

### 4.5 压缩包工具类 (AgentPackUtil.java)

```java
package com.kortex.ai.features.agent.tools.util;

import com.kortex.ai.features.agent.tools.dto.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Agent 打包工具类 - 负责 ZIP 文件的压缩和解压
 */
@Slf4j
@Component
public class AgentPackUtil {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String MANIFEST_FILE = "manifest.json";
    private static final String AGENTS_DIR = "agents/";
    private static final String KNOWLEDGE_DIR = "knowledge/";
    private static final String LIBRARY_FILE = "library.json";
    private static final String VECTORS_DIR = "vectors/";
    private static final String VECTORS_FILE = "vectors.json";
    
    /**
     * 创建导出 ZIP
     * 
     * @param manifest 清单
     * @param agents Agent 配置列表
     * @param library 知识库配置
     * @param vectorData 向量数据 (字节数组)
     * @return ZIP 文件字节数组
     */
    public byte[] createExportZip(ManifestDto manifest, 
                                   List<AgentConfigDto> agents,
                                   LibraryConfigDto library,
                                   byte[] vectorData) throws IOException {
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            
            // 1. 写入 manifest.json
            writeJsonToZip(zos, MANIFEST_FILE, manifest);
            
            // 2. 写入 agents/*.json
            for (int i = 0; i < agents.size(); i++) {
                String agentFile = AGENTS_DIR + (i + 1) + ".json";
                writeJsonToZip(zos, agentFile, agents.get(i));
            }
            
            // 3. 写入 knowledge/library.json
            if (library != null) {
                writeJsonToZip(zos, KNOWLEDGE_DIR + LIBRARY_FILE, library);
            }
            
            // 4. 写入 knowledge/vectors/vectors.json
            if (vectorData != null && vectorData.length > 0) {
                writeBytesToZip(zos, KNOWLEDGE_DIR + VECTORS_DIR + VECTORS_FILE, vectorData);
            }
            
            // 5. 计算 checksum 并更新 manifest
            manifest.setChecksum(calculateMD5(baos.toByteArray()));
        }
        
        return baos.toByteArray();
    }
    
    /**
     * 解析导入 ZIP
     * 
     * @param zipInputStream ZIP 输入流
     * @return 解析结果
     */
    public ImportPackage parseImportZip(InputStream zipInputStream) throws IOException {
        
        ImportPackage result = new ImportPackage();
        
        try (ZipInputStream zis = new ZipInputStream(zipInputStream, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                baos.reset();
                
                if (MANIFEST_FILE.equals(name)) {
                    // 读取 manifest
                    baos.transferTo(zis);
                    result.setManifest(objectMapper.readValue(baos.toString(), ManifestDto.class));
                    
                } else if (name.startsWith(AGENTS_DIR) && name.endsWith(".json")) {
                    // 读取 Agent 配置
                    baos.transferTo(zis);
                    AgentConfigDto agent = objectMapper.readValue(baos.toString(), AgentConfigDto.class);
                    result.getAgents().add(agent);
                    
                } else if (KNOWLEDGE_DIR + LIBRARY_FILE.equals(name)) {
                    // 读取知识库配置
                    baos.transferTo(zis);
                    result.setLibrary(objectMapper.readValue(baos.toString(), LibraryConfigDto.class));
                    
                } else if (name.equals(KNOWLEDGE_DIR + VECTORS_DIR + VECTORS_FILE)) {
                    // 读取向量数据
                    baos.transferTo(zis);
                    result.setVectorData(baos.toByteArray());
                }
                
                zis.closeEntry();
            }
        }
        
        return result;
    }
    
    /**
     * 写入 JSON 到 ZIP
     */
    private void writeJsonToZip(ZipOutputStream zos, String entryName, Object obj) throws IOException {
        byte[] data = objectMapper.writeValueAsBytes(obj);
        ZipEntry entry = new ZipEntry(entryName);
        entry.setSize(data.length);
        zos.putNextEntry(entry);
        zos.write(data);
        zos.closeEntry();
    }
    
    /**
     * 写入字节数组到 ZIP
     */
    private void writeBytesToZip(ZipOutputStream zos, String entryName, byte[] data) throws IOException {
        ZipEntry entry = new ZipEntry(entryName);
        entry.setSize(data.length);
        zos.putNextEntry(entry);
        zos.write(data);
        zos.closeEntry();
    }
    
    /**
     * 计算 MD5
     */
    private String calculateMD5(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return "md5:" + sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
    
    /**
     * 导入包解析结果
     */
    @lombok.Data
    public static class ImportPackage {
        private ManifestDto manifest;
        private List<AgentConfigDto> agents = new java.util.ArrayList<>();
        private LibraryConfigDto library;
        private byte[] vectorData;
    }
}
```

### 4.6 Python 服务调用接口 (PythonKnowledgeService.java)

```java
package com.kortex.ai.features.agent.tools.remote;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.kortex.ai.features.agent.tools.dto.LibraryConfigDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Python 知识库服务调用 - 用于导出/导入向量数据
 */
@Slf4j
@Component
public class PythonKnowledgeService {
    
    @Value("${ai.python-agent.url:http://localhost:9399}")
    private String pythonServiceUrl;
    
    /**
     * 导出知识库向量数据
     * 
     * @param libraryId 知识库 ID
     * @param agentCode Agent 编码
     * @return 向量数据 (JSON 格式的字节数组)
     */
    public byte[] exportVectors(String libraryId, String agentCode) {
        String endpoint = pythonServiceUrl + "/api/knowledge/export";
        
        log.info("[Python] 导出向量数据: libraryId={}, agentCode={}", libraryId, agentCode);
        
        try {
            JSONObject requestBody = new JSONObject();
            requestBody.set("library_id", libraryId);
            requestBody.set("agent_code", agentCode);
            requestBody.set("format", "json");  // 返回 JSON 格式
            
            HttpResponse response = HttpRequest.post(endpoint)
                    .body(JSONUtil.toJsonStr(requestBody))
                    .timeout(600000)  // 10 分钟超时 (9万文件可能很大)
                    .execute();
            
            if (!response.isOk()) {
                throw new RuntimeException("导出向量失败: " + response.body());
            }
            
            return response.bodyBytes();
            
        } catch (Exception e) {
            log.error("[Python] 导出向量异常", e);
            throw new RuntimeException("导出向量数据失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 导入知识库向量数据
     * 
     * @param agentCode Agent 编码 (新)
     * @param libraryId 知识库 ID (新)
     * @param vectorData 向量数据
     * @return 导入结果
     */
    public Map<String, Object> importVectors(String agentCode, String libraryId, byte[] vectorData) {
        String endpoint = pythonServiceUrl + "/api/knowledge/import";
        
        log.info("[Python] 导入向量数据: agentCode={}, libraryId={}, size={}", 
                agentCode, libraryId, vectorData.length);
        
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("agent_code", agentCode);
            requestBody.put("library_id", libraryId);
            requestBody.put("vectors", new String(vectorData, "UTF-8"));
            
            HttpResponse response = HttpRequest.post(endpoint)
                    .body(JSONUtil.toJsonStr(requestBody))
                    .timeout(600000)  // 10 分钟超时
                    .execute();
            
            if (!response.isOk()) {
                throw new RuntimeException("导入向量失败: " + response.body());
            }
            
            return JSONUtil.toBean(response.body(), Map.class);
            
        } catch (Exception e) {
            log.error("[Python] 导入向量异常", e);
            throw new RuntimeException("导入向量数据失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 创建知识库 Collection
     * 
     * @param agentCode Agent 编码
     * @param libraryName 知识库名称
     * @param dimension 向量维度
     * @return 创建结果
     */
    public Map<String, Object> createCollection(String agentCode, String libraryName, int dimension) {
        String endpoint = pythonServiceUrl + "/api/knowledge/collection/create";
        
        try {
            JSONObject requestBody = new JSONObject();
            requestBody.set("agent_code", agentCode);
            requestBody.set("collection_name", libraryName);
            requestBody.set("dimension", dimension);
            requestBody.set("metric_type", "COSINE");
            
            HttpResponse response = HttpRequest.post(endpoint)
                    .body(JSONUtil.toJsonStr(requestBody))
                    .timeout(60000)
                    .execute();
            
            if (!response.isOk()) {
                throw new RuntimeException("创建 Collection 失败: " + response.body());
            }
            
            return JSONUtil.toBean(response.body(), Map.class);
            
        } catch (Exception e) {
            log.error("[Python] 创建 Collection 异常", e);
            throw new RuntimeException("创建知识库 Collection 失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 检查 Collection 是否存在
     * 
     * @param agentCode Agent 编码
     * @return 是否存在
     */
    public boolean collectionExists(String agentCode) {
        String endpoint = pythonServiceUrl + "/api/knowledge/collection/exists";
        
        try {
            JSONObject requestBody = new JSONObject();
            requestBody.set("agent_code", agentCode);
            
            HttpResponse response = HttpRequest.post(endpoint)
                    .body(JSONUtil.toJsonStr(requestBody))
                    .timeout(10000)
                    .execute();
            
            if (response.isOk()) {
                JSONObject result = JSONUtil.parseObj(response.body());
                return result.getBool("exists", false);
            }
            
            return false;
            
        } catch (Exception e) {
            log.warn("[Python] 检查 Collection 异常: {}", e.getMessage());
            return false;
        }
    }
}
```

### 4.7 导出服务实现 (AgentExportServiceImpl.java)

```java
package com.kortex.ai.features.agent.tools.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kortex.ai.features.agent.tools.dto.*;
import com.kortex.ai.features.agent.tools.remote.PythonKnowledgeService;
import com.kortex.ai.features.agent.tools.service.AgentExportService;
import com.kortex.ai.features.agent.tools.util.AgentPackUtil;
import com.kortex.common.core.exception.ServiceException;
import com.kortex.system.domain.SysAiAgent;
import com.kortex.system.mapper.SysAiAgentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Agent 导出服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentExportServiceImpl implements AgentExportService {
    
    private final SysAiAgentMapper agentMapper;
    private final AgentPackUtil packUtil;
    private final PythonKnowledgeService pythonService;
    
    /**
     * 获取可导出的 Agent 列表
     */
    @Override
    public List<AgentConfigDto> listExportableAgents() {
        // 查询所有 Agent (可按租户过滤)
        LambdaQueryWrapper<SysAiAgent> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysAiAgent::getStatus, "0");  // 只导出正常的
        
        List<SysAiAgent> agents = agentMapper.selectList(wrapper);
        
        return agents.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
    
    /**
     * 导出 Agent
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public byte[] exportAgent(Long agentId, boolean includeVectors) {
        // 1. 查询 Agent
        SysAiAgent agent = agentMapper.selectById(agentId);
        if (agent == null) {
            throw new ServiceException("Agent 不存在: " + agentId);
        }
        
        // 2. 转换为 DTO
        AgentConfigDto agentDto = convertToDto(agent);
        
        // 3. 构建清单
        ManifestDto manifest = new ManifestDto();
        manifest.setVersion("1.0");
        manifest.setExportTime(LocalDateTime.now());
        manifest.setAgentCount(1);
        manifest.setCreatedBy("admin");  // TODO: 从登录上下文获取
        
        List<ManifestDto.AgentSummaryDto> summaries = new ArrayList<>();
        ManifestDto.AgentSummaryDto summary = new ManifestDto.AgentSummaryDto();
        summary.setAgentCode(agent.getAgentCode());
        summary.setAgentName(agent.getAgentName());
        summary.setLibraryName(agentDto.getKbLibraryName());
        summaries.add(summary);
        manifest.setAgents(summaries);
        
        // 4. 获取知识库配置 (如果有绑定)
        LibraryConfigDto libraryDto = null;
        byte[] vectorData = null;
        
        if (includeVectors && agent.getKbLibraryId() != null) {
            try {
                // 调用 Python 服务导出向量数据
                vectorData = pythonService.exportVectors(agent.getKbLibraryId(), agent.getAgentCode());
                
                // 构建知识库配置
                libraryDto = new LibraryConfigDto();
                libraryDto.setLibraryId(agent.getKbLibraryId());
                libraryDto.setLibraryName(agentDto.getKbLibraryName());
                libraryDto.setLibraryType("knowledge");
                libraryDto.setVectorDbType("milvus");
                libraryDto.setCollectionName(agent.getAgentCode());
                libraryDto.setDimension(1536);  // TODO: 从 Python 服务获取实际维度
                libraryDto.setVectorCount(agentDto.getFileCount() != null ? 
                        agentDto.getFileCount().longValue() : 0L);
                
            } catch (Exception e) {
                log.error("导出向量数据失败", e);
                throw new ServiceException("导出向量数据失败: " + e.getMessage());
            }
        }
        
        // 5. 生成 ZIP
        try {
            List<AgentConfigDto> agents = new ArrayList<>();
            agents.add(agentDto);
            
            return packUtil.createExportZip(manifest, agents, libraryDto, vectorData);
            
        } catch (Exception e) {
            log.error("生成导出文件失败", e);
            throw new ServiceException("生成导出文件失败: " + e.getMessage());
        }
    }
    
    @Override
    public byte[] exportAgents(List<Long> agentIds, boolean includeVectors, boolean includeFiles) {
        // TODO: 批量导出逻辑
        throw new UnsupportedOperationException("批量导出功能待实现");
    }
    
    /**
     * Agent 实体转换为 DTO
     */
    private AgentConfigDto convertToDto(SysAiAgent agent) {
        AgentConfigDto dto = new AgentConfigDto();
        dto.setAgentCode(agent.getAgentCode());
        dto.setAgentName(agent.getAgentName());
        dto.setIcon(agent.getIcon());
        dto.setDescription(agent.getDescription());
        dto.setSystemPrompt(agent.getSystemPrompt());
        dto.setRoutingKeywords(agent.getRoutingKeywords());
        dto.setToolsEnabled(agent.getToolsEnabled());
        dto.setFallbackStrategy(agent.getFallbackStrategy());
        dto.setSort(agent.getSort());
        dto.setStatus(agent.getStatus());
        dto.setKbLibraryId(agent.getKbLibraryId());
        dto.setKbLibraryName(agent.getKbLibraryName());  // TODO: 需要查询
        dto.setFileCount(agent.getFileCount());           // TODO: 需要查询
        dto.setPermittedDeptIds(agent.getPermittedDeptIds());
        dto.setPermittedRoleKeys(agent.getPermittedRoleKeys());
        return dto;
    }
}
```

### 4.8 导入服务实现 (AgentImportServiceImpl.java)

```java
package com.kortex.ai.features.agent.tools.service.impl;

import cn.hutool.core.util.IdUtil;
import com.kortex.ai.features.agent.tools.dto.*;
import com.kortex.ai.features.agent.tools.remote.PythonKnowledgeService;
import com.kortex.ai.features.agent.tools.service.AgentImportService;
import com.kortex.ai.features.agent.tools.util.AgentPackUtil;
import com.kortex.common.core.exception.ServiceException;
import com.kortex.system.domain.SysAiAgent;
import com.kortex.system.mapper.SysAiAgentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.util.*;

/**
 * Agent 导入服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentImportServiceImpl implements AgentImportService {
    
    private final SysAiAgentMapper agentMapper;
    private final AgentPackUtil packUtil;
    private final PythonKnowledgeService pythonService;
    
    /**
     * 预览导入文件
     */
    @Override
    public ManifestDto previewImport(MultipartFile file) {
        try {
            AgentPackUtil.ImportPackage pkg = packUtil.parseImportZip(file.getInputStream());
            return pkg.getManifest();
        } catch (Exception e) {
            log.error("预览导入文件失败", e);
            throw new ServiceException("预览导入文件失败: " + e.getMessage());
        }
    }
    
    /**
     * 执行导入
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentImportResultDto importAgents(MultipartFile file,
                                             Map<String, String> agentCodeMapping,
                                             Map<String, String> libraryIdMapping) {
        try {
            // 1. 解析 ZIP
            AgentPackUtil.ImportPackage pkg = packUtil.parseImportZip(file.getInputStream());
            ManifestDto manifest = pkg.getManifest();
            
            if (manifest == null) {
                throw new ServiceException("无效的导入文件：缺少清单");
            }
            
            // 2. 生成 ID 映射
            Map<String, String> finalAgentCodeMap = new HashMap<>();
            Map<String, String> finalLibraryIdMap = new HashMap<>();
            
            for (AgentConfigDto agent : pkg.getAgents()) {
                String originalCode = agent.getAgentCode();
                String originalLibraryId = agent.getKbLibraryId();
                
                // 生成新的 Agent 编码
                String newCode = agentCodeMapping != null && agentCodeMapping.containsKey(originalCode)
                        ? agentCodeMapping.get(originalCode)
                        : generateNewAgentCode(originalCode);
                
                // 生成新的知识库 ID
                String newLibraryId = libraryIdMapping != null && libraryIdMapping.containsKey(originalLibraryId)
                        ? libraryIdMapping.get(originalLibraryId)
                        : generateNewLibraryId();
                
                finalAgentCodeMap.put(originalCode, newCode);
                finalLibraryIdMap.put(originalLibraryId, newLibraryId);
                
                agent.setAgentCode(newCode);
                agent.setKbLibraryId(newLibraryId);
            }
            
            // 3. 创建 Agent 记录
            List<Long> importedAgentIds = new ArrayList<>();
            for (AgentConfigDto agentDto : pkg.getAgents()) {
                Long agentId = createAgentRecord(agentDto);
                importedAgentIds.add(agentId);
            }
            
            // 4. 导入向量数据 (如果存在)
            int importedVectorCount = 0;
            if (pkg.getVectorData() != null && pkg.getVectorData().length > 0) {
                // 为每个 Agent 导入向量
                for (AgentConfigDto agentDto : pkg.getAgents()) {
                    try {
                        // 创建 Collection
                        pythonService.createCollection(
                                agentDto.getAgentCode(),
                                agentDto.getAgentName(),
                                pkg.getLibrary() != null ? pkg.getLibrary().getDimension() : 1536
                        );
                        
                        // 导入向量
                        Map<String, Object> result = pythonService.importVectors(
                                agentDto.getAgentCode(),
                                agentDto.getKbLibraryId(),
                                pkg.getVectorData()
                        );
                        
                        importedVectorCount += (int) result.getOrDefault("imported_count", 0);
                        
                    } catch (Exception e) {
                        log.error("导入向量数据失败: agentCode={}", agentDto.getAgentCode(), e);
                        // 继续导入其他 Agent
                    }
                }
            }
            
            // 5. 返回结果
            AgentImportResultDto result = new AgentImportResultDto();
            result.setSuccess(true);
            result.setImportedAgentCount(importedAgentIds.size());
            result.setImportedAgentIds(importedAgentIds);
            result.setImportedVectorCount(importedVectorCount);
            result.setAgentCodeMapping(finalAgentCodeMap);
            result.setLibraryIdMapping(finalLibraryIdMap);
            
            return result;
            
        } catch (Exception e) {
            log.error("导入失败", e);
            throw new ServiceException("导入失败: " + e.getMessage());
        }
    }
    
    /**
     * 创建 Agent 数据库记录
     */
    private Long createAgentRecord(AgentConfigDto dto) {
        SysAiAgent agent = new SysAiAgent();
        agent.setAgentCode(dto.getAgentCode());
        agent.setAgentName(dto.getAgentName());
        agent.setIcon(dto.getIcon());
        agent.setDescription(dto.getDescription());
        agent.setSystemPrompt(dto.getSystemPrompt());
        agent.setRoutingKeywords(dto.getRoutingKeywords());
        agent.setToolsEnabled(dto.getToolsEnabled());
        agent.setFallbackStrategy(dto.getFallbackStrategy());
        agent.setSort(dto.getSort() != null ? dto.getSort() : 0);
        agent.setStatus(dto.getStatus() != null ? dto.getStatus() : "0");
        agent.setKbLibraryId(dto.getKbLibraryId());
        agent.setPermittedDeptIds(dto.getPermittedDeptIds());
        agent.setPermittedRoleKeys(dto.getPermittedRoleKeys());
        agent.setVersion(1L);
        
        agentMapper.insert(agent);
        
        log.info("创建 Agent 记录成功: agentId={}, agentCode={}", agent.getAgentId(), agent.getAgentCode());
        
        return agent.getAgentId();
    }
    
    /**
     * 生成新的 Agent 编码
     */
    private String generateNewAgentCode(String originalCode) {
        return originalCode + "_" + IdUtil.fastSimpleUUID().substring(0, 6);
    }
    
    /**
     * 生成新的知识库 ID
     */
    private String generateNewLibraryId() {
        return "lib_" + IdUtil.fastSimpleUUID();
    }
    
    @Override
    public AgentImportResultDto importAgentConfigs(List<AgentConfigDto> agents) {
        // TODO: 纯配置导入 (不含向量数据)
        throw new UnsupportedOperationException("纯配置导入功能待实现");
    }
}
```

### 4.9 Controller (AgentToolController.java)

```java
package com.kortex.ai.features.agent.tools.controller;

import com.kortex.ai.features.agent.tools.dto.*;
import com.kortex.ai.features.agent.tools.service.AgentExportService;
import com.kortex.ai.features.agent.tools.service.AgentImportService;
import com.kortex.common.core.domain.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Agent 导出/导入工具 Controller
 * 
 * 提供开发者工具接口，用于跨租户导出/导入 Agent
 */
@Slf4j
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/ai-tools/agent")
public class AgentToolController {
    
    private final AgentExportService exportService;
    private final AgentImportService importService;
    
    /**
     * 获取可导出的 Agent 列表
     */
    @GetMapping("/list")
    public R<List<AgentConfigDto>> listExportableAgents() {
        List<AgentConfigDto> agents = exportService.listExportableAgents();
        return R.ok(agents);
    }
    
    /**
     * 导出 Agent
     * 
     * @param agentId Agent ID
     * @param includeVectors 是否包含向量数据 (默认 true)
     * @return ZIP 文件流
     */
    @GetMapping("/export/{agentId}")
    public ResponseEntity<byte[]> exportAgent(
            @PathVariable Long agentId,
            @RequestParam(defaultValue = "true") boolean includeVectors) {
        
        log.info("导出 Agent: agentId={}, includeVectors={}", agentId, includeVectors);
        
        byte[] zipData = exportService.exportAgent(agentId, includeVectors);
        
        // 生成文件名
        String fileName = "agent_" + agentId + "_" + 
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + 
                ".zip";
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", fileName);
        headers.setContentLength(zipData.length);
        
        return new ResponseEntity<>(zipData, headers, HttpStatus.OK);
    }
    
    /**
     * 预览导入文件
     * 
     * @param file 导入的 ZIP 文件
     * @return 预览信息
     */
    @PostMapping("/import/preview")
    public R<ManifestDto> previewImport(@RequestParam("file") MultipartFile file) {
        
        if (file.isEmpty()) {
            return R.fail("请选择要导入的文件");
        }
        
        if (!file.getOriginalFilename().endsWith(".zip")) {
            return R.fail("只支持 .zip 格式的文件");
        }
        
        try {
            ManifestDto manifest = importService.previewImport(file);
            return R.ok(manifest);
        } catch (Exception e) {
            log.error("预览导入文件失败", e);
            return R.fail("预览失败: " + e.getMessage());
        }
    }
    
    /**
     * 执行导入
     * 
     * @param file 导入的 ZIP 文件
     * @param agentCodeMappings Agent 编码映射 (JSON 格式: {"原编码":"新编码"})
     * @param libraryIdMappings 知识库 ID 映射 (JSON 格式: {"原ID":"新ID"})
     * @return 导入结果
     */
    @PostMapping("/import")
    public R<AgentImportResultDto> importAgents(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "agentCodeMappings", required = false) String agentCodeMappings,
            @RequestParam(value = "libraryIdMappings", required = false) String libraryIdMappings) {
        
        if (file.isEmpty()) {
            return R.fail("请选择要导入的文件");
        }
        
        try {
            // 解析映射参数
            Map<String, String> agentCodeMap = null;
            Map<String, String> libraryIdMap = null;
            
            // TODO: 使用 Jackson 或 Hutool 解析 JSON 字符串
            
            AgentImportResultDto result = importService.importAgents(file, agentCodeMap, libraryIdMap);
            return R.ok(result);
            
        } catch (Exception e) {
            log.error("导入失败", e);
            return R.fail("导入失败: " + e.getMessage());
        }
    }
}
```

---

## 五、前端页面设计

### 5.1 导出页面 (AgentExport.vue)

#### 页面路由
```
/ai-tools/agent-export
```

#### 页面布局

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  Agent 跨租户导出工具                                            [返回]      │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  选择要导出的 Agent                                                         │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                                                                     │   │
│  │  ☑ 法律咨询助手                    知识库: 法律文书库 (9万文件)    │   │
│  │    编码: legal_assistant                                            │   │
│  │    描述: 专门用于法律咨询的 AI 助手                                  │   │
│  │                                                                     │   │
│  │  ─────────────────────────────────────────────────────────────      │   │
│  │                                                                     │   │
│  │  ☐ 合同审核助手                    知识库: 合同模板库 (2千文件)    │   │
│  │    编码: contract_reviewer                                           │   │
│  │    描述: 合同审核专用助手                                            │   │
│  │                                                                     │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  导出选项                                                                   │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                                                                     │   │
│  │  ☑ 包含向量数据  [必选]                                            │   │
│  │     说明: 导出知识库的向量数据，导入后可直接搜索                     │   │
│  │                                                                     │   │
│  │  ☐ 包含源文件                                                       │   │
│  │     说明: 导出原始文件内容 (文件较大，建议不勾选)                   │   │
│  │                                                                     │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                                                                     │   │
│  │                     开始导出                                         │   │
│  │                                                                     │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  [导出进度条区域 - 仅在导出中显示]                                          │
│                                                                             │
│  ✓ 导出完成!                                                              │
│  文件: legal_assistant_20260318.zip (2.3 GB)                              │
│                                                                             │
│  [    下载文件    ]                                                        │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

#### 核心组件

| 组件 | 说明 |
|------|------|
| AgentList | Agent 列表选择组件 |
| ExportOptions | 导出选项复选框 |
| ProgressBar | 导出进度条 |
| DownloadButton | 下载按钮 |

#### API 调用

| 接口 | 方法 | 说明 |
|------|------|------|
| `GET /ai-tools/agent/list` | 获取可导出的 Agent 列表 |
| `GET /ai-tools/agent/export/{agentId}` | 导出 Agent (返回文件流) |

---

### 5.2 导入页面 (AgentImport.vue)

#### 页面路由
```
/ai-tools/agent-import
```

#### 页面布局

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  Agent 跨租户导入工具                                            [返回]      │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  上传导入文件                                                                │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                                                                     │   │
│  │           ┌─────────────────────────────────────┐                   │   │
│  │           │                                     │                   │   │
│  │           │    点击选择文件 或 拖拽到此处       │                   │   │
│  │           │                                     │                   │   │
│  │           │         支持 .zip 格式              │                   │   │
│  │           │                                     │                   │   │
│  │           └─────────────────────────────────────┘                   │   │
│  │                                                                     │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  文件预览 (上传后显示)                                                       │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │  包信息                                                              │   │
│  │  ─────────────────────────────────────────────────────────────       │   │
│  │  文件名: legal_assistant_20260318.zip                               │   │
│  │  版本: 1.0                                                           │   │
│  │  导出时间: 2026-03-18 10:30:00                                      │   │
│  │  导出租户: 租户A                                                     │   │
│  │  导出人: admin                                                       │   │
│  │                                                                     │   │
│  │  包含内容                                                            │   │
│  │  ─────────────────────────────────────────────────────────────       │   │
│  │  • Agent: 法律咨询助手                                               │   │
│  │  • 知识库: 法律文书库 (9万文件)                                     │   │
│  │  • 向量数据: 已包含 (2.1 GB)                                        │   │
│  │                                                                     │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  导入配置 (可选)                                                            │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                                                                     │   │
│  │  Agent 编码: [legal_assistant_demo    ] (可修改，为空则自动生成)   │   │
│  │  知识库 ID: [lib_legal_demo        ] (可修改，为空则自动生成)      │   │
│  │                                                                     │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  ⚠ 导入后将在当前租户创建新的 Agent 和知识库                                │
│                                                                             │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                     开始导入                                         │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                                                             │
│  导入进度 (仅在导入中显示)                                                  │
│  ─────────────────────────────────────────────────────────────            │
│  Step 1: 解析压缩包... ✓ 完成                                             │
│  Step 2: 创建 Agent 记录... ✓ 完成                                       │
│  Step 3: 导入向量数据... ████████░░ 80%                                  │
│  Step 4: 重建索引... ░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░ │
│                                                                             │
│  ✓ 导入完成!                                                              │
│  成功导入: 1 个 Agent, 90000 条向量数据                                    │
│  新 Agent 编码: legal_assistant_demo                                      │
│                                                                             │
│  [    去 Agent 列表页面    ]                                               │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

#### 核心组件

| 组件 | 说明 |
|------|------|
| FileUploader | 文件上传组件 (支持拖拽) |
| FilePreview | 文件预览组件 |
| ImportConfigForm | 导入配置表单 |
| ImportProgress | 导入进度组件 |

#### API 调用

| 接口 | 方法 | 说明 |
|------|------|------|
| `POST /ai-tools/agent/import/preview` | 预览导入文件 |
| `POST /ai-tools/agent/import` | 执行导入 |

---

## 六、Python 端接口设计

### 6.1 需要 Python 端实现的接口

由于向量数据存储在 Python 服务的向量数据库中，需要 Python 端提供以下接口：

#### 6.1.1 导出向量数据

**接口**: `POST /api/knowledge/export`

**请求**:
```json
{
  "library_id": "lib_legal_001",
  "agent_code": "legal_assistant",
  "format": "json"
}
```

**响应**:
```json
{
  "success": true,
  "collection_name": "legal_assistant",
  "total_vectors": 90000,
  "dimension": 1536,
  "data": [
    {
      "id": "vec_001",
      "text": "合同编号：2024-001\n甲方：...",
      "filepath": "/contracts/2024/001.pdf",
      "metadata": {
        "title": "劳动合同模板",
        "category": "合同"
      },
      "vector": [0.123, -0.456, 0.789, ...]
    }
  ]
}
```

#### 6.1.2 导入向量数据

**接口**: `POST /api/knowledge/import`

**请求**:
```json
{
  "agent_code": "legal_assistant_demo",
  "library_id": "lib_legal_demo",
  "collection_name": "legal_assistant_demo",
  "vectors": "..."  // JSON 字符串
}
```

**响应**:
```json
{
  "success": true,
  "imported_count": 90000,
  "failed_count": 0,
  "duration_ms": 120000
}
```

#### 6.1.3 创建 Collection

**接口**: `POST /api/knowledge/collection/create`

**请求**:
```json
{
  "agent_code": "legal_assistant_demo",
  "collection_name": "legal_assistant_demo",
  "dimension": 1536,
  "metric_type": "COSINE"
}
```

**响应**:
```json
{
  "success": true,
  "collection_name": "legal_assistant_demo"
}
```

#### 6.1.4 检查 Collection 是否存在

**接口**: `POST /api/knowledge/collection/exists`

**请求**:
```json
{
  "agent_code": "legal_assistant"
}
```

**响应**:
```json
{
  "exists": true
}
```

---

## 七、流程时序图

### 7.1 导出流程

```
┌─────────┐    ┌─────────┐    ┌─────────┐    ┌─────────┐    ┌─────────┐
│ 前端    │    │ Controller│   │ ExportSvc│   │  DB     │    │ Python  │
└────┬────┘    └────┬────┘    └────┬────┘    └────┬────┘    └────┬────┘
     │              │              │              │              │
     │ 1.GET /list  │              │              │              │
     │─────────────▶│              │              │              │
     │              │              │              │              │
     │              │ 2.listExportableAgents()    │              │
     │              │─────────────▶│              │              │
     │              │◀────────────│              │              │
     │◀─────────────│              │              │              │
     │              │              │              │              │
     │ 3.GET /export/{id}          │              │              │
     │─────────────▶│              │              │              │
     │              │              │              │              │
     │              │ 4.exportAgent(id)            │              │
     │              │─────────────▶│              │              │
     │              │◀────────────│              │              │
     │              │              │              │              │
     │              │              │ 5.exportVectors()            │
     │              │              │─────────────▶│              │
     │              │              │◀────────────│              │
     │              │              │              │              │
     │              │ 6.createZip()│              │              │
     │              │─────────────▶│              │              │
     │              │◀────────────│              │              │
     │              │              │              │              │
     │ 7.返回ZIP    │              │              │              │
     │◀─────────────│              │              │              │
     │              │              │              │              │
```

### 7.2 导入流程

```
┌─────────┐    ┌─────────┐    ┌─────────┐    ┌─────────┐    ┌─────────┐
│ 前端    │    │ Controller│   │ ImportSvc│   │  DB     │    │ Python  │
└────┬────┘    └────┬────┘    └────┬────┘    └────┬────┘    └────┬────┘
     │              │              │              │              │
     │ 1.POST /import/preview     │              │              │
     │─────────────▶│              │              │              │
     │              │              │              │              │
     │              │ 2.parseZip() │              │              │
     │              │─────────────▶│              │              │
     │              │◀────────────│              │              │
     │◀─────────────│              │              │              │
     │              │              │              │              │
     │ 2.POST /import             │              │              │
     │─────────────▶│              │              │              │
     │              │              │              │              │
     │              │ 3.parseZip() │              │              │
     │              │─────────────▶│              │              │
     │              │◀────────────│              │              │
     │              │              │              │              │
     │              │ 4.生成ID映射│              │              │
     │              │─────────────▶│              │              │
     │              │◀────────────│              │              │
     │              │              │              │              │
     │              │ 5.insertAgent()            │              │
     │              │─────────────▶│─────────────▶│              │
     │              │◀────────────│◀────────────│              │
     │              │              │              │              │
     │              │ 6.createCollection()       │              │
     │              │─────────────▶│─────────────▶│              │
     │              │◀────────────│◀────────────│              │
     │              │              │              │              │
     │              │ 7.importVectors()          │              │
     │              │─────────────▶│─────────────▶│              │
     │              │◀────────────│◀────────────│              │
     │              │              │              │              │
     │ 8.返回结果  │              │              │              │
     │◀─────────────│              │              │              │
     │              │              │              │              │
```

---

## 八、配置文件

### 8.1 application.yml 配置

```yaml
# Agent 导出/导入工具配置
ai:
  tools:
    # 导出临时目录
    temp-dir: /tmp/agent-export
    # 最大导入文件大小 (MB)
    max-import-size: 5120
    # 导出超时时间 (毫秒)
    export-timeout: 600000
    # 导入超时时间 (毫秒)
    import-timeout: 600000
    
python-agent:
  url: ${ai.python-agent.url:http://localhost:9399}
```

---

## 九、错误处理

### 9.1 常见错误及处理

| 错误码 | 错误信息 | 处理方式 |
|-------|---------|----------|
| 10001 | Agent 不存在 | 检查 agentId 是否正确 |
| 10002 | 导出文件过大 | 建议不勾选"包含源文件" |
| 10003 | Python 服务不可用 | 检查 Python 服务是否启动 |
| 10004 | 向量导出失败 | 检查向量数据库连接 |
| 10005 | 导入文件格式错误 | 确保是有效的 ZIP 文件 |
| 10006 | Agent 编码重复 | 修改导入配置中的新编码 |
| 10007 | 向量导入失败 | 检查目标 Python 服务状态 |

---

## 十、实施步骤

### 10.1 开发顺序

| 步骤 | 任务 | 预估工作量 |
|------|------|-----------|
| 1 | 创建 DTO 类 (ManifestDto, AgentConfigDto, LibraryConfigDto 等) | 0.5 天 |
| 2 | 开发 AgentPackUtil 压缩包工具类 | 1 天 |
| 3 | 开发 PythonKnowledgeService Python 服务调用 | 0.5 天 |
| 4 | 开发 AgentExportService 导出服务 | 1 天 |
| 5 | 开发 AgentImportService 导入服务 | 1 天 |
| 6 | 开发 AgentToolController 接口 | 0.5 天 |
| 7 | 开发前端导出页面 | 1 天 |
| 8 | 开发前端导入页面 | 1 天 |
| 9 | Python 端接口开发 | 2 天 |
| 10 | 联调测试 | 2 天 |

**总计**: 约 10 天

---

## 十一、风险与限制

### 11.1 风险点

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| 向量数据过大 | 9万文件的向量数据可能超过 2GB，ZIP 压缩后仍很大 | 建议不包含源文件 |
| Python 服务版本差异 | 不同版本的向量数据库可能不兼容 | 版本校验 |
| 导入中断 | 大文件导入可能中断 | 事务控制，支持重试 |
| agentCode 重复 | 目标租户可能已有相同编码 | 强制生成新编码 |

### 11.2 限制

1. **文件大小限制**: 最大 5GB
2. **向量维度**: 当前仅支持 1536 维 (Ada-002)
3. **向量数据库**: 仅支持 Milvus/Qdrant

---

## 十二、总结

本方案提供了一个完整的 Agent 跨租户导出/导入工具的技术实现方案，核心要点：

1. **数据结构**: Agent 配置 + 知识库绑定关系 + 向量数据
2. **传输格式**: ZIP 压缩包 (包含 JSON 元数据和向量数据)
3. **实现层级**: Java 端 (Controller + Service) + Python 端 (向量数据库)
4. **前端**: 两个简单页面 (导出 + 导入)

该工具为临时演示场景设计，部署简单，使用方便，满足跨租户共享 Agent 的需求。

---

*文档版本: 1.0*
*创建日期: 2026-03-18*
