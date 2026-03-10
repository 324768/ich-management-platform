package com.hyang.ich.omnitrix.infrastructure.telemetry;

import com.hyang.ich.omnitrix.mapper.AiTraceLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Token→RMB 成本追踪器。
 *
 * 按模型名称匹配定价（元/千Token），计算每次请求的实际成本，
 * 并记录到 ai_trace_log.cost_rmb 字段。
 *
 * 定价表可按需更新，支持模糊匹配（模型名前缀）。
 */
@Slf4j
@Component
public class CostTracker {

    /** 定价表：模型前缀 → [输入价格, 输出价格]（元/千Token） */
    private static final Map<String, double[]> PRICING = new LinkedHashMap<>();

    static {
        // DeepSeek 系列
        PRICING.put("deepseek-chat", new double[]{0.001, 0.002});
        PRICING.put("deepseek-reasoner", new double[]{0.004, 0.016});
        PRICING.put("deepseek", new double[]{0.001, 0.002});

        // Qwen 系列
        PRICING.put("qwen-max", new double[]{0.02, 0.06});
        PRICING.put("qwen-plus", new double[]{0.004, 0.012});
        PRICING.put("qwen-turbo", new double[]{0.002, 0.006});
        PRICING.put("qwen-long", new double[]{0.0005, 0.002});
        PRICING.put("qwen", new double[]{0.004, 0.012});

        // GLM 系列
        PRICING.put("glm-4-plus", new double[]{0.05, 0.05});
        PRICING.put("glm-4", new double[]{0.1, 0.1});
        PRICING.put("glm-3-turbo", new double[]{0.005, 0.005});
        PRICING.put("glm", new double[]{0.005, 0.005});

        // 通用兜底（保守估价）
        PRICING.put("default", new double[]{0.01, 0.01});
    }

    private final AiTraceLogMapper traceLogMapper;

    public CostTracker(AiTraceLogMapper traceLogMapper) {
        this.traceLogMapper = traceLogMapper;
    }

    /**
     * 计算并记录本次请求成本。
     *
     * @param traceId      追踪 ID
     * @param model        使用的模型名称
     * @param inputTokens  输入 Token 数
     * @param outputTokens 输出 Token 数
     * @return 本次请求成本（元）
     */
    public BigDecimal recordCost(String traceId, String model, int inputTokens, int outputTokens) {
        BigDecimal cost = calculateCost(model, inputTokens, outputTokens);
        try {
            traceLogMapper.updateCostRmb(traceId, cost);
            log.debug("成本记录: traceId={}, model={}, tokens(in={}, out={}), cost=¥{}",
                    traceId, model, inputTokens, outputTokens, cost);
        } catch (Exception e) {
            log.error("成本记录失败: traceId={}, error={}", traceId, e.getMessage());
        }
        return cost;
    }

    /**
     * 计算成本（不写库，纯计算）。
     */
    public BigDecimal calculateCost(String model, int inputTokens, int outputTokens) {
        double[] prices = resolvePricing(model);
        double inputCost = inputTokens * prices[0] / 1000.0;
        double outputCost = outputTokens * prices[1] / 1000.0;
        return BigDecimal.valueOf(inputCost + outputCost).setScale(6, RoundingMode.HALF_UP);
    }

    /**
     * 模型定价解析（前缀匹配）。
     */
    private double[] resolvePricing(String model) {
        if (model == null) return PRICING.get("default");
        String lower = model.toLowerCase();
        for (Map.Entry<String, double[]> entry : PRICING.entrySet()) {
            if (lower.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return PRICING.get("default");
    }

    /**
     * 查询成本统计（最近 N 天）。
     */
    public Map<String, Object> getCostStats(int days) {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("days", days);
        stats.put("totalCostRmb", traceLogMapper.sumCostRmb(days));
        stats.put("avgCostPerRequest", traceLogMapper.avgCostRmb(days));
        stats.put("costByModel", traceLogMapper.costByModel(days));
        stats.put("dailyCost", traceLogMapper.dailyCost(days));
        return stats;
    }
}
