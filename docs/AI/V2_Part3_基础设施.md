# Part 3: 基础设施

## 12. LLM 基础设施

### 12.1 LlmProperties (配置属性)

**文件**: `infrastructure/llm/LlmProperties.java`

```java
@ConfigurationProperties(prefix = "omnitrix.llm")
public class LlmProperties {
    // 顶层字段(向后兼容单模型配置)
    private String apiUrl;
    private String model;
    private String apiKey;
    private int timeoutSeconds = 120;
    private int maxTokens = 2048;
    private double temperature = 0.7;
    private int maxContextTokens = 3000;       // 上下文窗口Token上限
    private boolean langchain4jEnabled = false; // ★ V2 Feature Flag

    // 双模型配置
    private ModelConfig primary;
    private ModelConfig auxiliary;

    @Data
    public static class ModelConfig {
        private String apiUrl, model, apiKey;
        private int timeoutSeconds = 120, maxTokens = 2048;
        private double temperature = 0.7;
    }

    @PostConstruct
    public void init() {
        // 未显式配置primary时，从顶层字段构建
        if (primary == null) {
            primary = new ModelConfig();
            primary.setApiUrl(apiUrl); primary.setModel(model);
            primary.setApiKey(apiKey); // ...
        }
        // auxiliary默认复用primary配置但调低参数
        if (auxiliary == null) {
            auxiliary = new ModelConfig();
            BeanUtils.copyProperties(primary, auxiliary);
            auxiliary.setMaxTokens(1024);
            auxiliary.setTemperature(0.3);
        }
    }

    public ModelConfig getPrimaryConfig() { return primary; }
    public ModelConfig getAuxiliaryConfig() { return auxiliary; }
}
```

### 12.2 LangChain4jConfig (模型Bean)

**文件**: `config/LangChain4jConfig.java`

```java
@Configuration
public class LangChain4jConfig {
    @Bean
    public ChatLanguageModel chatLanguageModel(LlmProperties props) {
        ModelConfig cfg = props.getPrimaryConfig();
        return OpenAiChatModel.builder()
                .baseUrl(convertBaseUrl(cfg.getApiUrl()))
                .modelName(cfg.getModel())
                .apiKey(cfg.getApiKey())
                .timeout(Duration.ofSeconds(cfg.getTimeoutSeconds()))
                .maxTokens(cfg.getMaxTokens())
                .temperature(cfg.getTemperature())
                .build();
    }

    @Bean
    public StreamingChatLanguageModel streamingChatLanguageModel(LlmProperties props) {
        ModelConfig cfg = props.getPrimaryConfig();
        return OpenAiStreamingChatModel.builder()
                .baseUrl(convertBaseUrl(cfg.getApiUrl()))
                .modelName(cfg.getModel())
                .apiKey(cfg.getApiKey())
                .timeout(Duration.ofSeconds(cfg.getTimeoutSeconds()))
                .maxTokens(cfg.getMaxTokens())
                .temperature(cfg.getTemperature())
                .build();
    }

    // SiliconFlow API需要去掉 /chat/completions 后缀
    private String convertBaseUrl(String apiUrl) {
        if (apiUrl != null && apiUrl.endsWith("/chat/completions"))
            return apiUrl.replace("/chat/completions", "");
        return apiUrl;
    }
}
```

### 12.3 LlmClient (同步LLM客户端)

**文件**: `infrastructure/llm/LlmClient.java`

核心能力:
- 同步HTTP调用OpenAI Chat Completions兼容API
- **指数退避重试**: `BASE_DELAY * (1L << attempt) + random(0, BASE)`，防止雷鸣群效应
- **响应缓存**: ConcurrentHashMap + 5分钟TTL (辅助模型调用结果)
- **双模型**: `chat()` 主模型 / `chatAuxiliaryJson()` 辅助模型
- **@PreDestroy**: 正确关闭缓存清理ScheduledExecutorService

```java
@Component
public class LlmClient {
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_BASE_DELAY_MS = 1000;
    private static final long CACHE_TTL_MS = 300_000; // 5分钟

    private final ConcurrentHashMap<String, CachedResponse> cache = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cacheCleanerExecutor;

    // 主模型同步调用
    public LlmResponse chat(String systemPrompt, List<Map<String,String>> history, String userMessage) {
        return chatWithConfig(llmProperties.getPrimaryConfig(), systemPrompt, history, userMessage);
    }

    // 指定模型配置调用
    public LlmResponse chatWithConfig(ModelConfig config, String systemPrompt,
                                       List<Map<String,String>> history, String userMessage) {
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                // 构建 OpenAI 格式请求体
                // POST到 config.getApiUrl()
                // 解析响应中的 content, usage.prompt_tokens, usage.completion_tokens
                return LlmResponse.of(content, inputTokens, outputTokens);
            } catch (Exception e) {
                if (attempt < MAX_RETRIES - 1) {
                    long delay = RETRY_BASE_DELAY_MS * (1L << attempt)
                                 + ThreadLocalRandom.current().nextLong(RETRY_BASE_DELAY_MS);
                    Thread.sleep(delay);
                } else throw e;
            }
        }
    }

    // 辅助模型JSON调用(用于ToolSelector/MemoryExtractor/MasterBrain决策)
    public LlmResponse chatAuxiliaryJson(String systemPrompt, List<Map<String,String>> history, String userMessage) {
        // 使用 auxiliary 配置 + 缓存
    }

    @PreDestroy
    public void shutdown() {
        cacheCleanerExecutor.shutdownNow();
    }
}
```

### 12.4 LlmStreamHandler (流式处理)

**文件**: `infrastructure/llm/LlmStreamHandler.java`

```java
@Component
public class LlmStreamHandler {
    private final LlmCircuitBreaker circuitBreaker;

    public StreamResult streamChat(String systemPrompt, List<Map<String,String>> history,
                                    String userMessage, SseEmitter emitter,
                                    SseEmitterManager sseManager) {
        // 熔断器检查
        if (!circuitBreaker.allowRequest()) {
            sseManager.sendError(emitter, "AI服务暂时过载，请稍后重试");
            return StreamResult.error("circuit_open");
        }

        try {
            // SSE流式读取LLM响应
            // <think>标签分流处理:
            StringBuilder thinkBuffer = new StringBuilder();
            boolean insideThink = false;
            String partialTag = "";  // 跨chunk标签缓冲

            // 每个chunk:
            //   检查是否包含 <think> 或 </think>
            //   insideThink=true 时 → sseManager.sendThinking(chunk)
            //   insideThink=false 时 → sseManager.sendChunk(chunk)
            //   跨chunk标签分割: 缓冲不完整标签待下次chunk拼接

            circuitBreaker.recordSuccess();
            return StreamResult.success(fullContent, inputTokens, outputTokens);
        } catch (Exception e) {
            circuitBreaker.recordFailure();
            return StreamResult.error(e.getMessage());
        }
    }
}
```

### 12.5 LlmCircuitBreaker (熔断器)

```java
@Component
public class LlmCircuitBreaker {
    private static final int FAILURE_THRESHOLD = 5;   // 连续失败5次触发熔断
    private static final int SUCCESS_THRESHOLD = 2;   // 半开状态连续成功2次恢复
    private static final long RECOVERY_TIME_MS = 60000; // 熔断恢复等待1分钟

    private enum State { CLOSED, OPEN, HALF_OPEN }
    private volatile State state = State.CLOSED;
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicInteger successCount = new AtomicInteger(0);
    private volatile long lastFailureTime = 0;

    public boolean allowRequest() {
        switch (state) {
            case CLOSED: return true;
            case OPEN:
                if (System.currentTimeMillis() - lastFailureTime > RECOVERY_TIME_MS) {
                    state = State.HALF_OPEN;
                    return true;
                }
                return false;
            case HALF_OPEN: return true;
            default: return true;
        }
    }

    public void recordSuccess() {
        if (state == State.HALF_OPEN) {
            if (successCount.incrementAndGet() >= SUCCESS_THRESHOLD) {
                state = State.CLOSED;
                failureCount.set(0); successCount.set(0);
            }
        } else { failureCount.set(0); }
    }

    public void recordFailure() {
        lastFailureTime = System.currentTimeMillis();
        if (failureCount.incrementAndGet() >= FAILURE_THRESHOLD) {
            state = State.OPEN;
        }
    }

    // 健康检查用
    public String getState() { return state.name(); }
    public boolean isDegraded() { return state != State.CLOSED; }
}
```

### 12.6 LlmResponse

```java
public class LlmResponse {
    private String content;
    private int inputTokens;
    private int outputTokens;
    public static LlmResponse of(String content, int in, int out) { ... }
}
```

---

## 13. 提示词工程

### 13.1 PromptManager (三层回退)

```java
@Component
public class PromptManager {
    private final ConcurrentHashMap<String, CachedPrompt> cache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 300_000; // 5分钟

    public String getPrompt(String key) {
        // Layer 1: 缓存
        CachedPrompt cached = cache.get(key);
        if (cached != null && !cached.isExpired()) return cached.getValue();

        // Layer 2: 数据库 (ai_prompt_config)
        AiPromptConfig config = promptConfigMapper.selectByKey(key);
        if (config != null) {
            cache.put(key, new CachedPrompt(config.getContent()));
            return config.getContent();
        }

        // Layer 3: YML配置
        String ymlValue = environment.getProperty("omnitrix.prompts." + key);
        if (ymlValue != null) return ymlValue;

        // Layer 4: 代码常量
        return PromptTemplate.getDefault(key);
    }

    public void invalidateCache() { cache.clear(); }
}
```

### 13.2 PromptAssembler (多层级拼接)

```java
@Component
public class PromptAssembler {

    public String assemble(AgentContext ctx, SubAgent agent, AgentQueryResult result,
                           String summary, String userProfile) {
        StringBuilder sb = new StringBuilder();

        // 层级1: 基础Prompt
        sb.append(promptManager.getPrompt("base_system_prompt")).append("\n\n");

        // 层级1b: 动态Skills
        String skills = buildSkillsSection(ctx);
        if (!skills.isEmpty()) sb.append(skills).append("\n\n");

        // 层级2a: 当前时间
        sb.append("当前时间: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
          .append("\n\n");

        // 层级2b: 用户身份
        sb.append("当前用户ID: ").append(ctx.getUserId()).append("\n\n");

        // 层级2b+: 用户画像(L3长期记忆)
        if (userProfile != null && !userProfile.isEmpty()) {
            sb.append("## 用户画像\n").append(userProfile).append("\n\n");
        }

        // 层级2c: 对话摘要
        if (summary != null && !summary.isEmpty()) {
            sb.append("## 历史对话摘要\n").append(summary).append("\n\n");
        }

        // 层级3: Agent角色Prompt
        if (agent != null && agent.getAgentPrompt() != null) {
            sb.append(agent.getAgentPrompt()).append("\n\n");
        }

        // 层级4a: 系统全局记忆
        String systemMemory = systemMemoryService.getActiveMemory();
        if (systemMemory != null) {
            sb.append("## 系统记忆\n").append(systemMemory).append("\n\n");
        }

        // 层级4b: 查询结果注入
        if (result != null && result.getData() != null) {
            sb.append(result.toPromptInjection()).append("\n\n");
        }

        return sb.toString();
    }

    // 向后兼容: 4参数版本
    public String assemble(AgentContext ctx, SubAgent agent, AgentQueryResult result, String summary) {
        return assemble(ctx, agent, result, summary, null);
    }
}
```

### 13.3 PromptTemplate (常量)

```java
public class PromptTemplate {
    public static final String BASE_SYSTEM = """
        你是 Omnitrix 智能助手，服务于非物质文化遗产传承平台。
        请遵循以下原则：
        1. 基于提供的数据回答，不编造信息
        2. 使用友好的中文表达
        3. 回答简洁准确，必要时列举要点
        4. 涉及敏感操作时提醒用户确认
        """;

    // 更多默认模板...
    public static String getDefault(String key) {
        switch (key) {
            case "base_system_prompt": return BASE_SYSTEM;
            default: return "";
        }
    }
}
```

---

## 14. 记忆系统

### 14.1 ChatMemoryManager (L1 会话记忆)

**文件**: `infrastructure/memory/ChatMemoryManager.java`

```java
@Component
public class ChatMemoryManager {
    private final RedisChatMemoryStore store;
    private final int maxContextTokens; // 从LlmProperties注入，默认3000

    // 加载历史（带Token裁剪）
    public List<Map<String,String>> loadHistory(String sessionId) {
        List<Map<String,String>> messages = store.getMessages(sessionId);
        return trimByTokenLimit(messages);
    }

    // Token裁剪: 从最新消息向前保留，直到达到Token上限
    private List<Map<String,String>> trimByTokenLimit(List<Map<String,String>> messages) {
        int totalTokens = 0;
        List<Map<String,String>> trimmed = new ArrayList<>();
        for (int i = messages.size() - 1; i >= 0; i--) {
            int tokens = estimateTokens(messages.get(i).get("content"));
            if (totalTokens + tokens > maxContextTokens) break;
            totalTokens += tokens;
            trimmed.add(0, messages.get(i));
        }
        return trimmed;
    }

    // CJK Token估算: 中文字符算2 token，英文算0.5
    public int estimateTokens(String text) {
        if (text == null) return 0;
        int count = 0;
        for (char c : text.toCharArray()) {
            if (c >= 0x4E00 && c <= 0x9FFF) count += 2;  // CJK
            else count += 1;
        }
        return count / 2;  // 粗略: 2 chars ≈ 1 token
    }

    public void appendMessage(String sessionId, String role, String content) {
        store.appendMessage(sessionId, role, content);
    }

    public String getSummary(String sessionId) {
        return store.getSummary(sessionId);
    }
}
```

### 14.2 RedisChatMemoryStore (Redis存储)

**文件**: `infrastructure/memory/RedisChatMemoryStore.java` (190行)

```java
@Component
public class RedisChatMemoryStore {
    private static final String SESSION_PREFIX = "omnitrix:session:";
    private static final String LIST_PREFIX = "omnitrix:list:";
    private static final String SUMMARY_PREFIX = "omnitrix:summary:";
    private static final long TTL_SECONDS = 7 * 24 * 3600;  // 7天
    private static final int MAX_LIST_SIZE = 100;

    // 原子追加消息 (RPUSH)
    public void appendMessage(String sessionId, String role, String content) {
        String key = LIST_PREFIX + sessionId;
        String json = serialize(role, content);
        try {
            redisTemplate.opsForList().rightPush(key, json);  // RPUSH原子操作
            redisTemplate.expire(key, TTL_SECONDS, TimeUnit.SECONDS);
            // 超过MAX_LIST_SIZE时裁剪
            Long size = redisTemplate.opsForList().size(key);
            if (size != null && size > MAX_LIST_SIZE) {
                redisTemplate.opsForList().trim(key, size - MAX_LIST_SIZE, -1);
            }
        } catch (Exception e) {
            // 降级: GET整个列表 → 追加 → SET
            fallbackAppend(sessionId, role, content);
        }
    }

    // 原子替换列表 (MULTI/EXEC事务)
    public void replaceList(String sessionId, List<Map<String,String>> messages) {
        String key = LIST_PREFIX + sessionId;
        redisTemplate.execute(new SessionCallback<>() {
            @Override
            public Object execute(RedisOperations operations) {
                operations.multi();
                operations.delete(key);
                for (Map<String,String> msg : messages) {
                    operations.opsForList().rightPush(key, serialize(msg));
                }
                operations.expire(key, TTL_SECONDS, TimeUnit.SECONDS);
                return operations.exec();
            }
        });
    }

    public List<Map<String,String>> getMessages(String sessionId) { ... }
    public void saveSummary(String sessionId, String summary) { ... }
    public String getSummary(String sessionId) { ... }
    public long getMessageCount(String sessionId) { ... }
}
```

### 14.3 MemoryExtractor (L3 长期记忆提取)

**文件**: `infrastructure/memory/MemoryExtractor.java`

```java
@Component
public class MemoryExtractor {

    @Async("aiAsyncExecutor")
    public void extractAndSave(Long userId, String userMessage, String aiResponse) {
        try {
            // 1. 检查记忆上限 (50条/用户)
            int currentCount = userMemoryMapper.countByUserId(userId);
            if (currentCount >= 50) return;

            // 2. 加载已有记忆 (去重用)
            List<AiUserMemory> existing = userMemoryMapper.selectByUserId(userId);
            String existingText = existing.stream()
                .map(m -> m.getMemoryKey() + ":" + m.getMemoryValue())
                .collect(Collectors.joining("\n"));

            // 3. 构建提取Prompt
            String prompt = """
                分析以下对话，提取有长期价值的用户信息。
                判断标准：三个月后这条信息还有用吗？

                已有记忆（避免重复）:
                %s

                输出JSON数组: [{"type":"preference/interest/fact/decision/lesson",
                                "key":"简短标题","value":"详细内容","confidence":0.0-1.0}]
                如果没有可提取的信息，返回空数组 []
                """.formatted(existingText);

            // 4. 调用辅助LLM提取
            String conversation = "用户: " + userMessage + "\nAI: " + aiResponse;
            LlmResponse response = llmClient.chatAuxiliaryJson(prompt, List.of(), conversation);

            // 5. 解析JSON，保存/更新记忆
            List<MemoryItem> items = parseMemoryItems(response.getContent());
            for (MemoryItem item : items) {
                AiUserMemory existingMem = userMemoryMapper.selectByUserIdAndKey(userId, item.getKey());
                if (existingMem != null) {
                    userMemoryMapper.updateValue(existingMem.getId(), item.getValue(), item.getConfidence());
                } else {
                    AiUserMemory mem = new AiUserMemory();
                    mem.setUserId(userId);
                    mem.setMemoryType(item.getType());
                    mem.setMemoryKey(item.getKey());
                    mem.setMemoryValue(item.getValue());
                    mem.setConfidence(item.getConfidence());
                    mem.setSource("conversation");
                    userMemoryMapper.insert(mem);
                }
            }
        } catch (Exception e) {
            log.debug("记忆提取异常(非关键): {}", e.getMessage());
        }
    }
}
```

**5种记忆类型**: preference(偏好) / interest(兴趣) / fact(事实) / decision(决策) / lesson(经验)

### 14.4 MemorySummarizer (L2 对话摘要)

```java
@Component
public class MemorySummarizer {
    private static final int SUMMARIZE_THRESHOLD = 10; // 消息数超过阈值时触发

    @Async("aiAsyncExecutor")
    public void summarizeIfNeeded(String sessionId, Long conversationId) {
        long msgCount = chatMemoryStore.getMessageCount(sessionId);
        if (msgCount < SUMMARIZE_THRESHOLD) return;

        List<Map<String,String>> messages = chatMemoryStore.getMessages(sessionId);
        String prompt = "请简洁概括以下对话的要点，200字以内:\n" + formatMessages(messages);
        LlmResponse response = llmClient.chatAuxiliaryJson(prompt, List.of(), "");
        String summary = response.getContent();

        chatMemoryStore.saveSummary(sessionId, summary);
        conversationService.updateSummary(conversationId, summary);
    }
}
```

### 14.5 UserMemoryService (用户画像构建)

**文件**: `service/UserMemoryService.java`

```java
@Service
public class UserMemoryService {
    private static final String CACHE_PREFIX = "omnitrix:user_memory:";
    private static final long CACHE_TTL_MINUTES = 30;

    public String buildUserProfile(Long userId) {
        // 1. 检查Redis缓存
        String cached = redisTemplate.opsForValue().get(CACHE_PREFIX + userId);
        if (cached != null) return cached;

        // 2. 加载长期记忆 (ai_user_memory)
        List<AiUserMemory> memories = userMemoryMapper.selectByUserId(userId);

        // 3. 加载行为兴趣 (ai_user_behavior 近30天)
        List<AiUserBehavior> behaviors = behaviorMapper.selectRecentByUserId(userId, 30);

        // 4. 融合构建画像文本
        StringBuilder profile = new StringBuilder();
        if (!memories.isEmpty()) {
            Map<String, List<AiUserMemory>> grouped = memories.stream()
                .collect(Collectors.groupingBy(AiUserMemory::getMemoryType));
            for (var entry : grouped.entrySet()) {
                profile.append("【").append(typeLabel(entry.getKey())).append("】\n");
                for (AiUserMemory m : entry.getValue()) {
                    profile.append("- ").append(m.getMemoryKey()).append(": ")
                           .append(m.getMemoryValue()).append("\n");
                }
            }
        }
        if (!behaviors.isEmpty()) {
            profile.append("【近期兴趣】\n");
            for (AiUserBehavior b : behaviors) {
                profile.append("- ").append(b.getKeywords()).append("\n");
            }
        }

        String result = profile.toString();
        // 5. 写入缓存
        redisTemplate.opsForValue().set(CACHE_PREFIX + userId, result,
                CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        return result;
    }

    public void invalidateCache(Long userId) {
        redisTemplate.delete(CACHE_PREFIX + userId);
    }
}
```

### 14.6 SystemMemoryService (L4 系统记忆)

全局共享记忆，从 `ai_system_memory` 表加载活跃条目，注入到所有对话的System Prompt中。

---

## 15. 安全与护栏机制

### 15.1 GuardrailsFilter

**文件**: `infrastructure/guardrails/GuardrailsFilter.java` (160行)

#### 输入验证 validateInput()

```java
public String validateInput(String userMessage) {
    // 1. 空检查
    if (userMessage == null || userMessage.trim().isEmpty()) return "消息不能为空";

    // 2. 长度检查 (MAX_INPUT_LENGTH = 2000)
    if (userMessage.length() > 2000) return "消息过长，请控制在2000字以内";

    // 3. 控制字符检查 [\x00-\x08\x0b\x0c\x0e-\x1f]
    if (SPECIAL_CHAR_PATTERN.matcher(userMessage).find()) return "消息包含非法字符";

    // 4. Prompt Injection检测 (20+中英文注入模式)
    //    "ignore previous instructions", "忽略上述指令", "显示你的提示词" 等
    for (String pattern : INJECTION_PATTERNS) {
        if (lowerInput.contains(pattern.toLowerCase())) return "您的消息包含不允许的指令";
    }

    // 5. 敏感词检查 (暴力/恐怖/自杀/毒品/赌博/色情/反动/极端主义)
    for (String word : SENSITIVE_WORDS) {
        if (userMessage.contains(word)) return "您的消息包含敏感内容";
    }

    return null; // 通过
}
```

#### 输出清洗 sanitizeOutput()

```java
public String sanitizeOutput(String aiResponse) {
    // 1a. 移除 <think>...</think> 思维链 (含跨行 (?s) 匹配)
    sanitized = sanitized.replaceAll("(?s)<think>.*?</think>", "");

    // 1b. 移除残余HTML标签 (防XSS)
    sanitized = sanitized.replaceAll("<[^>]*>", "");

    // 2. 检查系统Prompt信息泄露 (记录日志)
    //    "system prompt", "系统提示词", "我的指令是" 等

    // 3. PII脱敏: 手机号 → 1xx****xxxx
    sanitized = sanitized.replaceAll("(1[3-9]\\d)\\d{4}(\\d{4})", "$1****$2");

    // 4. PII脱敏: 身份证 → xxxxxx********xxxx
    sanitized = sanitized.replaceAll("(\\d{6})\\d{8}(\\d{4})", "$1********$2");

    // 5. PII脱敏: 邮箱 → xxx***@domain
    sanitized = sanitized.replaceAll(
        "([a-zA-Z0-9._%+-]{1,3})[a-zA-Z0-9._%+-]*@([a-zA-Z0-9.-]+)", "$1***@$2");

    return sanitized;
}
```

### 15.2 RateLimiter (Redis滑动窗口限流)

**文件**: `infrastructure/guardrails/RateLimiter.java` (79行)

```java
@Component
public class RateLimiter {
    private static final String RATE_KEY_PREFIX = "ai:rate:";
    private static final int MAX_REQUESTS_PER_MINUTE = 15;
    private static final int WINDOW_SECONDS = 60;

    // Lua原子脚本: INCR + 首次EXPIRE
    private static final String SCRIPT =
        "local cnt = redis.call('INCR', KEYS[1])\n" +
        "if cnt == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end\n" +
        "return cnt";

    public boolean tryAcquire(Long userId) {
        String key = RATE_KEY_PREFIX + userId;
        Long count = redisTemplate.execute(script, List.of(key), String.valueOf(WINDOW_SECONDS));
        if (count != null && count > MAX_REQUESTS_PER_MINUTE) return false;
        return true;
        // Redis异常时放行，不阻塞业务
    }

    public int remaining(Long userId) {
        String val = redisTemplate.opsForValue().get(RATE_KEY_PREFIX + userId);
        return val == null ? MAX_REQUESTS_PER_MINUTE : Math.max(0, MAX_REQUESTS_PER_MINUTE - Integer.parseInt(val));
    }
}
```

### 15.3 TokenBudget (Token日预算)

**文件**: `infrastructure/guardrails/TokenBudget.java` (107行)

```java
@Component
public class TokenBudget {
    private static final String BUDGET_KEY_PREFIX = "ai:token_budget:";
    private static final int DAILY_TOKEN_LIMIT = 50_000;
    private static final int KEY_EXPIRE_SECONDS = 86_400 + 3600; // 25小时(跨日安全)

    // Lua原子脚本: INCRBY + 首次EXPIRE
    private static final String SCRIPT =
        "local cnt = redis.call('INCRBY', KEYS[1], ARGV[1])\n" +
        "if cnt == tonumber(ARGV[1]) then redis.call('EXPIRE', KEYS[1], ARGV[2]) end\n" +
        "return cnt";

    public boolean hasRemaining(Long userId) { /* 检查是否还有预算 */ }

    public long consume(Long userId, int tokens) {
        String key = BUDGET_KEY_PREFIX + userId + ":" + LocalDate.now().format(BASIC_ISO_DATE);
        return redisTemplate.execute(script, List.of(key),
                String.valueOf(tokens), String.valueOf(KEY_EXPIRE_SECONDS));
    }

    public int remaining(Long userId) { /* 返回今日剩余Token数 */ }
}
```

---

## 16. 流式输出与 SSE

### 16.1 SseEmitterManager

**文件**: `infrastructure/sse/SseEmitterManager.java`

```java
@Component
public class SseEmitterManager {
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Agent信息事件
    public void sendAgentInfo(SseEmitter emitter, String agentCode) {
        send(emitter, "agent", Map.of("code", agentCode));
    }

    // 文本块事件
    public void sendChunk(SseEmitter emitter, String chunk) {
        send(emitter, "chunk", chunk);
    }

    // 思维链事件
    public void sendThinking(SseEmitter emitter, String thinking) {
        send(emitter, "thinking", thinking);
    }

    // Agent调度事件 (前端"调用过程"展示)
    public void sendAgentDispatch(SseEmitter emitter, String code, String name, String task) {
        send(emitter, "agent_dispatch", Map.of("code", code, "name", name, "task", task));
    }

    // Agent结果事件
    public void sendAgentResult(SseEmitter emitter, String code, String name,
                                 String result, String status, int latencyMs) {
        send(emitter, "agent_result", Map.of("code", code, "name", name,
                "result", truncate(result, 500), "status", status, "latency", latencyMs));
    }

    // L2黑板事件
    public void sendL2Board(SseEmitter emitter, String status, int taskCount) {
        send(emitter, "l2_board", Map.of("status", status, "taskCount", taskCount));
    }

    public void sendL2Task(SseEmitter emitter, String taskId, String agentCode, String status) {
        send(emitter, "l2_task", Map.of("taskId", taskId, "agent", agentCode, "status", status));
    }

    // 完成事件 (含model元信息)
    public void sendDone(SseEmitter emitter, Long msgId, int latencyMs, String model) {
        send(emitter, "done", new DoneEvent(msgId, latencyMs, model));
        emitter.complete();
    }

    // 错误事件
    public void sendError(SseEmitter emitter, String message) {
        send(emitter, "error", message);
        emitter.complete();
    }

    @Data @AllArgsConstructor
    static class DoneEvent {
        private Long msgId;
        private int latency;
        private String model;
    }
}
```

### 16.2 SSE 事件类型完整参考

| 事件名 | 触发时机 | 数据格式 |
|--------|---------|----------|
| `agent` | 路由确定Agent后 | `{"code":"content_assistant"}` |
| `chunk` | LLM输出文本块 | 纯文本 |
| `thinking` | LLM `<think>`标签内容 | 纯文本 |
| `agent_dispatch` | Agent开始执行 | `{"code":"xx","name":"xx","task":"xx"}` |
| `agent_result` | Agent执行完成 | `{"code":"xx","result":"...","status":"success/error","latency":123}` |
| `l2_board` | Ultra L2黑板状态 | `{"status":"started/completed","taskCount":3}` |
| `l2_task` | Ultra L2任务进度 | `{"taskId":"t1","agent":"xx","status":"running/done/failed"}` |
| `done` | 完成 | `{"msgId":123,"latency":456,"model":"deepseek-v3"}` |
| `error` | 错误 | 错误文本 |

---

## 17. 遥测与监控

### 17.1 TelemetryTracer (链路追踪)

```java
@Component
public class TelemetryTracer {
    public String recordAndReturnTraceId(Long conversationId, Long userId, String userMessage,
            String intentCode, String agentCode, String model,
            int inputTokens, int outputTokens, int latencyMs) {
        // 1. 创建 AiTraceLog 记录
        AiTraceLog log = new AiTraceLog();
        log.setTraceId(UUID.randomUUID().toString());
        log.setConversationId(conversationId); log.setUserId(userId);
        log.setUserMessage(userMessage); log.setIntentCode(intentCode);
        log.setAgentCode(agentCode); log.setModel(model);
        log.setInputTokens(inputTokens); log.setOutputTokens(outputTokens);
        log.setLatencyMs(latencyMs);
        traceLogMapper.insert(log);

        // 2. 创建 Span (REQUEST → AGENT → LLM)
        createSpan(log.getTraceId(), "REQUEST", 0, latencyMs);
        createSpan(log.getTraceId(), "AGENT", 0, agentLatencyMs);
        createSpan(log.getTraceId(), "LLM", agentLatencyMs, latencyMs);

        return log.getTraceId();
    }
}
```

### 17.2 AiSelfEvaluator

```java
@Component
public class AiSelfEvaluator {
    @Async("aiAsyncExecutor")
    public void evaluate(String traceId, String userMessage, String aiResponse) {
        // 调用辅助LLM评估回答质量(1-10分)
        // 更新 AiTraceLog 的 selfEvalScore 字段
    }
}
```

### 17.3 CostTracker

```java
@Component
public class CostTracker {
    public void recordCost(String traceId, String model, int inputTokens, int outputTokens) {
        // 按模型定价计算成本
        // 保存到 AiTraceSpan 或更新 AiTraceLog
    }
}
```

### 17.4 TitleGenerator

```java
@Component
public class TitleGenerator {
    @Async("aiAsyncExecutor")
    public void generateTitle(Long conversationId, String firstMessage) {
        String prompt = "为以下对话生成一个简短的标题(10字以内):\n" + firstMessage;
        LlmResponse response = llmClient.chatAuxiliaryJson(prompt, List.of(), "");
        conversationService.updateTitle(conversationId, response.getContent().trim());
    }
}
```

### 17.5 健康检查端点

```java
// AiAdminController
@GetMapping("/health")
public Map<String, Object> healthCheck(@RequestParam Long adminId) {
    return Map.of(
        "circuitBreaker", circuitBreaker.getState(),
        "rateLimiterRemaining", rateLimiter.remaining(adminId),
        "tokenBudgetRemaining", tokenBudget.remaining(adminId),
        "isDegraded", circuitBreaker.isDegraded()
    );
}
```

---

## 18. 待确认操作 PendingAction

### 18.1 两轮确认流程

```
轮次1: 用户 "帮我报名剪纸活动"
  → @Tool registerActivity("剪纸") 执行
  → 搜索活动 → 创建 PendingAction{type="register_activity", params={activityId:123}}
  → 保存到 Redis (key: omnitrix:pending:{sessionId}, TTL: 5分钟)
  → 返回 "找到「非遗剪纸体验」活动，确认报名吗？回复'确认'执行或'取消'放弃"

轮次2: 用户 "确认"
  → OrchestratorService.handlePendingAction 检测到待确认
  → 从Redis读取PendingAction
  → ActionExecutor.execute(action, userId)
  → 删除Redis中的PendingAction
  → 返回 "已成功报名「非遗剪纸体验」活动！"
```

### 18.2 PendingAction DTO

```java
public class PendingAction {
    private String actionType;
    private String description;
    private Map<String, String> params;

    public static PendingAction of(String actionType, String description) { ... }
    public PendingAction param(String key, String value) {
        params.put(key, value); return this;
    }
}
```

### 18.3 ActionExecutor

```java
@Component
public class ActionExecutor {
    public String execute(PendingAction action, Long userId) {
        try {
            switch (action.getActionType()) {
                case "add_to_cart":       return doAddToCart(action, userId);
                case "register_activity": return doRegisterActivity(action, userId);
                case "cancel_order":      return doCancelOrder(action, userId);
                case "confirm_receive":   return doConfirmReceive(action, userId);
                case "set_default_address": return doSetDefaultAddress(action, userId);
                case "like_post":         return doLikePost(action, userId);
                case "favorite_post":     return doFavoritePost(action, userId);
                case "comment_post":      return doCommentPost(action, userId);
                // Admin操作
                case "publish_activity":  return doPublishActivity(action);
                case "update_product_status": return doUpdateProductStatus(action);
                case "send_notification": return doSendNotification(action);
                case "ship_order":        return doShipOrder(action);
                // Ultra操作
                case "ban_user":          return doBanUser(action);
                case "unban_user":        return doUnbanUser(action);
                case "disable_user_ai":   return doDisableUserAi(action);
                case "enable_user_ai":    return doEnableUserAi(action);
                default: return "[操作失败] 不支持的操作类型: " + action.getActionType();
            }
        } catch (Exception e) {
            return "[操作失败] " + e.getMessage();
        }
    }
}
```

### 18.4 确认/取消检测逻辑

```java
private boolean isConfirmMessage(String msg) {
    return msg.matches("(?i)(确认|确定|是|好|ok|yes|同意|执行|proceed)");
}
private boolean isCancelMessage(String msg) {
    return msg.matches("(?i)(取消|不|否|算了|no|cancel|放弃)");
}
```
