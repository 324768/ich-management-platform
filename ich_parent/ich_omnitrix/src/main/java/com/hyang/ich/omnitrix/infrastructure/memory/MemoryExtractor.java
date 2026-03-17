package com.hyang.ich.omnitrix.infrastructure.memory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hyang.ich.omnitrix.entity.AiUserMemory;
import com.hyang.ich.omnitrix.infrastructure.llm.LlmClient;
import com.hyang.ich.omnitrix.infrastructure.llm.LlmResponse;
import com.hyang.ich.omnitrix.mapper.AiUserMemoryMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 记忆提取器：异步从对话中提取用户事实，写入长期记忆表。
 * 对应 OpenClaw MEMORY.md 的自动写入机制。
 *
 * 提取类型：
 * - preference: 用户偏好（回复风格、沟通习惯）
 * - interest:   兴趣爱好（关注的非遗类型、文创品类）
 * - fact:       客观事实（购买过什么、参加过什么活动）
 * - decision:   重要决策（报名了活动、下了订单）
 * - lesson:     经验教训（用户纠正AI的错误等）
 *
 * 增强：笔记提取功能
 * - note:       从工具结果中提取的关键信息（实体ID、时间、地点等）
 * - 存储在 Redis 中（短期记忆）
 * - 注入到 System Prompt 的 ## 已记录的关键发现 段落
 */
@Slf4j
@Component
public class MemoryExtractor {

    private static final int MAX_MEMORIES_PER_USER = 50;
    private static final Pattern JSON_ARRAY_PATTERN = Pattern.compile("\\[\\s*\\{.*}\\s*]", Pattern.DOTALL);

    /** Redis key 前缀 */
    private static final String NOTE_KEY_PREFIX = "omnitrix:notes:";
    private static final Duration NOTE_TTL = Duration.ofDays(7);

    private static final String EXTRACT_PROMPT =
            "你是一个用户画像提取专家。请从以下对话中提取值得长期记住的用户信息。\n\n" +
            "【提取原则 — 三个月后还有用吗？】\n" +
            "✅ 应该提取：用户偏好、兴趣爱好、重要决策、购买/报名事实、经验教训\n" +
            "❌ 不应提取：临时信息(明天开会)、日常琐事、敏感隐私(密码)、重复已知信息\n\n" +
            "【输出格式】\n" +
            "严格输出JSON数组，每条记忆包含 type/key/value/confidence 四个字段。\n" +
            "如果没有值得提取的信息，输出空数组 []\n\n" +
            "type 取值: preference | interest | fact | decision | lesson\n" +
            "key: 简短标识(如 interest_heritage_type, purchase_preference)\n" +
            "value: 用自然语言描述的事实(不超过100字)\n" +
            "confidence: 0.5-1.0 之间的置信度\n\n" +
            "【示例输出】\n" +
            "[{\"type\":\"interest\",\"key\":\"interest_heritage_type\",\"value\":\"对苗族银饰特别感兴趣\",\"confidence\":0.9}," +
            "{\"type\":\"fact\",\"key\":\"purchase_history\",\"value\":\"购买过银手镯，评价满意\",\"confidence\":0.85}]\n\n" +
            "【已有记忆（避免重复）】\n%s\n\n" +
            "【本轮对话】\n用户: %s\nAI: %s\n\n" +
            "JSON数组:";

    /** 笔记提取 Prompt：从工具结果中提取关键信息 */
    private static final String NOTE_EXTRACT_PROMPT =
            "你是一个信息提取专家。从以下工具返回结果中提取关键信息，记录为\"笔记\"。\n\n" +
            "【笔记类型】\n" +
            "- entity_id: 实体ID（如活动ID、商品ID、订单ID）\n" +
            "- time: 时间信息（日期、时间点、活动时间）\n" +
            "- location: 地点信息（地址、活动地点）\n" +
            "- status: 状态信息（订单状态、审批状态）\n" +
            "- number: 数字信息（数量、价格、人数）\n" +
            "- name: 名称信息（人名、用户名）\n" +
            "- conclusion: 结论性信息（查询结果、操作结果）\n\n" +
            "【提取原则】\n" +
            "- 只提取对后续对话有帮助的关键信息\n" +
            "- ID类信息优先提取（可用于后续操作）\n" +
            "- 时间和地点信息需要提取\n" +
            "- 过于通用的信息不需要提取\n\n" +
            "【输出格式】（严格JSON）\n" +
            "有笔记: {\"notes\":[\n" +
            "  {\"type\":\"entity_id\",\"key\":\"activity_id_42\",\"value\":\"剪纸活动ID=42\",\"confidence\":0.95},\n" +
            "  {\"type\":\"time\",\"key\":\"activity_time\",\"value\":\"周六下午2点\",\"confidence\":0.9}\n" +
            "]}\n" +
            "无笔记: {\"notes\":[]}\n\n" +
            "【工具返回结果】\n%s\n\n" +
            "【已有笔记（避免重复）】\n%s\n\n" +
            "JSON:";

    private final LlmClient llmClient;
    private final AiUserMemoryMapper memoryMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public MemoryExtractor(LlmClient llmClient, AiUserMemoryMapper memoryMapper, StringRedisTemplate redisTemplate) {
        this.llmClient = llmClient;
        this.memoryMapper = memoryMapper;
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 异步从对话中提取用户记忆并持久化
     */
    @Async("aiAsyncExecutor")
    public void extractAndSave(Long userId, String userMessage, String aiResponse) {
        try {
            // 简单过滤: 太短的对话不值得提取
            if (userMessage == null || userMessage.length() < 5) return;
            if (aiResponse == null || aiResponse.length() < 10) return;

            // 检查记忆容量上限
            int currentCount = memoryMapper.countByUserId(userId);
            if (currentCount >= MAX_MEMORIES_PER_USER) {
                log.debug("用户记忆已达上限({}), 跳过提取: userId={}", MAX_MEMORIES_PER_USER, userId);
                return;
            }

            // 加载已有记忆（用于去重提示）
            List<AiUserMemory> existing = memoryMapper.selectByUserId(userId);
            String existingSummary = buildExistingSummary(existing);

            // 构建提取 Prompt
            String prompt = String.format(EXTRACT_PROMPT, existingSummary, userMessage, aiResponse);

            // 调用辅助LLM提取
            LlmResponse response = llmClient.chatAuxiliaryJson(prompt, new ArrayList<>(), "提取用户记忆");
            String content = response.getContent();
            if (content == null || content.trim().isEmpty()) return;

            // 解析JSON数组
            List<Map<String, Object>> memories = parseMemories(content);
            if (memories == null || memories.isEmpty()) return;

            // 逐条保存或更新
            int saved = 0;
            for (Map<String, Object> mem : memories) {
                if (saveOrUpdate(userId, mem, currentCount + saved)) {
                    saved++;
                }
            }
            if (saved > 0) {
                log.info("记忆提取完成: userId={}, 新增{}条", userId, saved);
            }

        } catch (Exception e) {
            log.error("记忆提取失败: userId={}, error={}", userId, e.getMessage());
        }
    }

    /**
     * 从工具结果中提取笔记（增强功能）
     *
     * 场景：用户查询"帮我看看有什么剪纸活动"，工具返回多个活动结果。
     * 笔记会提取：活动ID、时间、地点等，供后续"报名"操作使用。
     *
     * @param userId     用户ID
     * @param sessionId  会话ID（用于 Redis key）
     * @param agentCode  哪个 Agent 的工具结果
     * @param toolResult 工具返回结果
     */
    @Async("aiAsyncExecutor")
    public void extractNotes(Long userId, String sessionId, String agentCode, String toolResult) {
        if (toolResult == null || toolResult.length() < 20) return;
        if (agentCode == null) agentCode = "unknown";

        try {
            // 加载已有笔记（用于去重）
            String existingNotes = getExistingNotes(userId, sessionId);

            // 构建提取 Prompt
            String prompt = String.format(NOTE_EXTRACT_PROMPT, toolResult, existingNotes);

            // 调用辅助 LLM 提取
            LlmResponse response = llmClient.chatAuxiliaryJson(prompt, new ArrayList<>(), "提取笔记");
            String content = response.getContent();
            if (content == null || content.trim().isEmpty()) return;

            // 解析笔记
            List<Map<String, Object>> notes = parseNotes(content);
            if (notes == null || notes.isEmpty()) return;

            // 保存笔记到 Redis
            int saved = 0;
            for (Map<String, Object> note : notes) {
                if (saveNote(userId, sessionId, agentCode, note)) {
                    saved++;
                }
            }

            if (saved > 0) {
                log.info("笔记提取完成: userId={}, sessionId={}, agent={}, 新增{}条",
                        userId, sessionId, agentCode, saved);
            }

        } catch (Exception e) {
            log.error("笔记提取失败: userId={}, sessionId={}, error={}",
                    userId, sessionId, e.getMessage());
        }
    }

    /**
     * 获取当前会话的笔记，供 Prompt 注入使用
     *
     * @return 格式化的笔记字符串，注入到 System Prompt
     */
    public String getSessionNotes(Long userId, String sessionId) {
        try {
            String key = NOTE_KEY_PREFIX + userId + ":" + sessionId;
            Set<String> keys = redisTemplate.keys(key + ":*");
            if (keys == null || keys.isEmpty()) {
                return "";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("\n## 已记录的关键发现\n");
            for (String noteKey : keys) {
                String note = redisTemplate.opsForValue().get(noteKey);
                if (note != null && !note.isEmpty()) {
                    // 提取 key 名称
                    String shortKey = noteKey.substring(noteKey.lastIndexOf(":") + 1);
                    sb.append("- ").append(shortKey).append(": ").append(note).append("\n");
                }
            }
            return sb.toString();

        } catch (Exception e) {
            log.debug("获取笔记失败: userId={}, sessionId={}, error={}",
                    userId, sessionId, e.getMessage());
            return "";
        }
    }

    /**
     * 获取已有笔记（字符串形式）
     */
    private String getExistingNotes(Long userId, String sessionId) {
        try {
            String key = NOTE_KEY_PREFIX + userId + ":" + sessionId;
            Set<String> keys = redisTemplate.keys(key + ":*");
            if (keys == null || keys.isEmpty()) {
                return "（无已有笔记）";
            }
            StringBuilder sb = new StringBuilder();
            for (String noteKey : keys) {
                String note = redisTemplate.opsForValue().get(noteKey);
                if (note != null) {
                    String shortKey = noteKey.substring(noteKey.lastIndexOf(":") + 1);
                    sb.append("- ").append(shortKey).append(": ").append(note).append("\n");
                }
            }
            return sb.length() > 0 ? sb.toString() : "（无已有笔记）";
        } catch (Exception e) {
            return "（无已有笔记）";
        }
    }

    /**
     * 保存单条笔记到 Redis
     */
    private boolean saveNote(Long userId, String sessionId, String agentCode, Map<String, Object> note) {
        try {
            String type = (String) note.get("type");
            String key = (String) note.get("key");
            String value = (String) note.get("value");

            if (type == null || key == null || value == null) return false;

            // 去重：检查是否已有相同 key 的笔记
            String redisKey = NOTE_KEY_PREFIX + userId + ":" + sessionId + ":" + key;
            if (Boolean.TRUE.equals(redisTemplate.hasKey(redisKey))) {
                return false;
            }

            // 保存到 Redis
            redisTemplate.opsForValue().set(redisKey, value, NOTE_TTL);
            log.debug("笔记保存: userId={}, key={}, value={}", userId, key, value);
            return true;

        } catch (Exception e) {
            log.warn("保存笔记失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 解析笔记 JSON
     */
    private List<Map<String, Object>> parseNotes(String content) {
        try {
            Matcher matcher = JSON_ARRAY_PATTERN.matcher(content);
            String jsonStr = matcher.find() ? matcher.group() : content.trim();

            // 尝试两种格式：{"notes":[...]} 或 直接 [...]
            try {
                // 优先尝试直接解析为数组
                return objectMapper.readValue(jsonStr, new TypeReference<List<Map<String, Object>>>() {});
            } catch (Exception e1) {
                // 如果失败，尝试解析为对象并提取 notes 字段
                Map<String, Object> root = objectMapper.readValue(jsonStr, new TypeReference<Map<String, Object>>() {});
                if (root.containsKey("notes")) {
                    return (List<Map<String, Object>>) root.get("notes");
                }
                // 如果既不是数组也没有 notes 字段，返回空列表
                return new ArrayList<>();
            }

        } catch (Exception e) {
            log.debug("笔记JSON解析失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 解析LLM返回的JSON数组
     */
    private List<Map<String, Object>> parseMemories(String content) {
        try {
            Matcher matcher = JSON_ARRAY_PATTERN.matcher(content);
            String jsonStr = matcher.find() ? matcher.group() : content.trim();

            try {
                return objectMapper.readValue(jsonStr, new TypeReference<List<Map<String, Object>>>() {});
            } catch (Exception e1) {
                // LLM 可能返回单个对象而非数组，尝试包装为列表
                Map<String, Object> single = objectMapper.readValue(jsonStr, new TypeReference<Map<String, Object>>() {});
                if (single.containsKey("type") && single.containsKey("key")) {
                    return Collections.singletonList(single);
                }
                return new ArrayList<>();
            }
        } catch (Exception e) {
            log.debug("记忆JSON解析失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 保存或更新单条记忆: 相同key则更新value+confidence，新key则插入
     */
    private boolean saveOrUpdate(Long userId, Map<String, Object> mem, int currentTotal) {
        try {
            String type = (String) mem.get("type");
            String key = (String) mem.get("key");
            String value = (String) mem.get("value");
            Object confObj = mem.get("confidence");
            double conf = confObj instanceof Number ? ((Number) confObj).doubleValue() : 0.8;

            if (type == null || key == null || value == null) return false;
            if (value.length() > 500) value = value.substring(0, 500);

            // 检查是否已有同key记忆
            AiUserMemory existing = memoryMapper.selectByUserIdAndKey(userId, key);
            if (existing != null) {
                // 更新: 如果新置信度更高或信息更丰富
                if (conf >= existing.getConfidence().doubleValue() || value.length() > existing.getMemoryValue().length()) {
                    memoryMapper.updateValue(existing.getId(), value, BigDecimal.valueOf(conf));
                    log.debug("记忆更新: userId={}, key={}", userId, key);
                }
                return false; // 更新不算新增
            }

            // 容量检查
            if (currentTotal >= MAX_MEMORIES_PER_USER) return false;

            // 新增
            AiUserMemory memory = new AiUserMemory();
            memory.setUserId(userId);
            memory.setMemoryType(type);
            memory.setMemoryKey(key);
            memory.setMemoryValue(value);
            memory.setConfidence(BigDecimal.valueOf(conf));
            memory.setSource("conversation");
            memoryMapper.insert(memory);
            return true;

        } catch (Exception e) {
            log.warn("保存单条记忆失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 构建已有记忆摘要供LLM去重参考
     */
    private String buildExistingSummary(List<AiUserMemory> existing) {
        if (existing == null || existing.isEmpty()) {
            return "（无已有记忆）";
        }
        StringBuilder sb = new StringBuilder();
        for (AiUserMemory m : existing) {
            sb.append("- [").append(m.getMemoryType()).append("] ")
              .append(m.getMemoryValue()).append("\n");
        }
        return sb.toString();
    }
}
