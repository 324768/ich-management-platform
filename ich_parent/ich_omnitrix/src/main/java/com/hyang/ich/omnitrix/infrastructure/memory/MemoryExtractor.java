package com.hyang.ich.omnitrix.infrastructure.memory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hyang.ich.omnitrix.entity.AiUserMemory;
import com.hyang.ich.omnitrix.infrastructure.llm.LlmClient;
import com.hyang.ich.omnitrix.infrastructure.llm.LlmResponse;
import com.hyang.ich.omnitrix.mapper.AiUserMemoryMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
 */
@Slf4j
@Component
public class MemoryExtractor {

    private static final int MAX_MEMORIES_PER_USER = 50;
    private static final Pattern JSON_ARRAY_PATTERN = Pattern.compile("\\[\\s*\\{.*}\\s*]", Pattern.DOTALL);

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

    private final LlmClient llmClient;
    private final AiUserMemoryMapper memoryMapper;
    private final ObjectMapper objectMapper;

    public MemoryExtractor(LlmClient llmClient, AiUserMemoryMapper memoryMapper) {
        this.llmClient = llmClient;
        this.memoryMapper = memoryMapper;
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
     * 解析LLM返回的JSON数组
     */
    private List<Map<String, Object>> parseMemories(String content) {
        try {
            // 尝试从回复中提取 JSON 数组
            Matcher matcher = JSON_ARRAY_PATTERN.matcher(content);
            String jsonStr = matcher.find() ? matcher.group() : content.trim();
            return objectMapper.readValue(jsonStr, new TypeReference<List<Map<String, Object>>>() {});
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
