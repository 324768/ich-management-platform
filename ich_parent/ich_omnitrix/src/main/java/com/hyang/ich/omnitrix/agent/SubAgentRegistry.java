package com.hyang.ich.omnitrix.agent;

import com.hyang.ich.omnitrix.agent.impl.DynamicSubAgent;
import com.hyang.ich.omnitrix.entity.AiAgentConfig;
import com.hyang.ich.omnitrix.mapper.AiAgentConfigMapper;
import com.hyang.ich.omnitrix.service.KnowledgeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 子代理注册表：系统代理（@Component 硬编码）+ 动态代理（DB 配置）并行共存。
 * 动态代理具有版本感知缓存，Admin 后台编辑后 version+1 自动触发重建。
 */
@Slf4j
@Component
public class SubAgentRegistry {

    /** 系统内置代理（不可变） */
    private final Map<String, SubAgent> systemAgents = new HashMap<>();

    /** 动态代理缓存（版本感知） */
    private final Map<String, DynamicSubAgent> dynamicAgents = new ConcurrentHashMap<>();

    /** 版本检查时间戳缓存：避免每次访问都查 DB，TTL 内直接复用 */
    private final Map<String, Long> versionCheckTimestamps = new ConcurrentHashMap<>();
    private static final long VERSION_CHECK_INTERVAL_MS = 30_000L; // 30 秒

    private final AiAgentConfigMapper agentConfigMapper;
    private final KnowledgeService knowledgeService;
    private final ObjectProvider<SubAgent> subAgentObjectProvider;

    public SubAgentRegistry(ObjectProvider<SubAgent> subAgentObjectProvider,
                            AiAgentConfigMapper agentConfigMapper,
                            KnowledgeService knowledgeService) {
        this.subAgentObjectProvider = subAgentObjectProvider;
        this.agentConfigMapper = agentConfigMapper;
        this.knowledgeService = knowledgeService;
    }

    @PostConstruct
    public void loadSystemAndDynamicAgents() {
        // 延迟获取所有 SubAgent，避免循环依赖
        List<SubAgent> agents = subAgentObjectProvider.orderedStream().toList();

        // 注册系统内置代理
        for (SubAgent agent : agents) {
            // 排除动态代理本身（DynamicSubAgent 是内部类，不通过此路径注册）
            if (!(agent instanceof DynamicSubAgent)) {
                systemAgents.put(agent.getCode(), agent);
                log.info("注册系统代理: [{}] {}", agent.getCode(), agent.getName());
            }
        }

        // 加载动态代理
        loadDynamicAgents();
    }

    /**
     * 从数据库加载动态代理
     */
    private void loadDynamicAgents() {
        try {
            List<AiAgentConfig> configs = agentConfigMapper.selectEnabled();
            for (AiAgentConfig config : configs) {
                // 动态代理不覆盖系统代理
                if (systemAgents.containsKey(config.getAgentCode())) {
                    log.warn("动态代理 [{}] 与系统代理冲突，跳过", config.getAgentCode());
                    continue;
                }
                DynamicSubAgent dynamic = new DynamicSubAgent(config, knowledgeService);
                dynamicAgents.put(config.getAgentCode(), dynamic);
                log.info("注册动态代理: [{}] {} (version={})", config.getAgentCode(), config.getAgentName(), config.getVersion());
            }
        } catch (Exception e) {
            log.warn("加载动态代理失败(可能表尚未创建): {}", e.getMessage());
        }
    }

    /**
     * 获取代理：优先系统代理 → 动态代理（版本检查）
     */
    public SubAgent get(String code) {
        // 系统代理优先
        SubAgent system = systemAgents.get(code);
        if (system != null) {
            return system;
        }

        // 动态代理（带版本检查）
        return getOrRefreshDynamic(code);
    }

    public SubAgent getOrDefault(String code) {
        SubAgent agent = get(code);
        if (agent == null) {
            agent = systemAgents.get("general_assistant");
        }
        return agent;
    }

    /**
     * 获取所有已注册的代理编码
     */
    public Set<String> getAllCodes() {
        Set<String> codes = new HashSet<>(systemAgents.keySet());
        codes.addAll(dynamicAgents.keySet());
        return codes;
    }

    /**
     * 获取所有动态代理（供 IntentRouter 读取路由关键词）
     */
    public Collection<DynamicSubAgent> getDynamicAgents() {
        return dynamicAgents.values();
    }

    /**
     * 刷新所有动态代理（Admin 编辑后调用）
     */
    public void refreshDynamicAgents() {
        dynamicAgents.clear();
        loadDynamicAgents();
        log.info("动态代理已全量刷新, 当前数量: {}", dynamicAgents.size());
    }

    /**
     * 版本感知：检查单个动态代理是否需要重建
     */
    private SubAgent getOrRefreshDynamic(String code) {
        DynamicSubAgent cached = dynamicAgents.get(code);

        // TTL 内直接复用缓存，不查 DB
        Long lastCheck = versionCheckTimestamps.get(code);
        if (cached != null && lastCheck != null
                && (System.currentTimeMillis() - lastCheck) < VERSION_CHECK_INTERVAL_MS) {
            return cached;
        }

        try {
            Long latestVersion = agentConfigMapper.selectVersionByCode(code);
            versionCheckTimestamps.put(code, System.currentTimeMillis());

            if (latestVersion == null) {
                // Agent 已停用或删除
                dynamicAgents.remove(code);
                versionCheckTimestamps.remove(code);
                return null;
            }

            if (cached != null && cached.getVersion().equals(latestVersion)) {
                return cached; // 版本未变，复用缓存
            }

            // 版本过期或新 Agent，重建
            AiAgentConfig config = agentConfigMapper.selectByCode(code);
            if (config != null) {
                DynamicSubAgent fresh = new DynamicSubAgent(config, knowledgeService);
                dynamicAgents.put(code, fresh);
                log.info("动态代理 [{}] 已重建 (version: {} → {})",
                        code, cached != null ? cached.getVersion() : "null", latestVersion);
                return fresh;
            }
        } catch (Exception e) {
            log.debug("动态代理版本检查失败: code={}, error={}", code, e.getMessage());
        }

        return cached; // 降级返回旧缓存
    }
}
