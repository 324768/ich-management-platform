package com.hyang.ich.omnitrix.agent.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 工具注册表 — 按角色（user/admin/ultra）管理 LangChain4j @Tool 提供者。
 * <p>
 * 每个 Tool 提供者是一个 Spring Bean，其方法上标注 @Tool 注解。
 * MasterBrainFactory 通过 getToolsForRole() 获取角色对应的工具对象列表，
 * 传递给 AiServices.builder().tools() 注册。
 */
@Slf4j
@Component
public class ToolRegistry {

    private final Map<String, List<Object>> roleTools = new LinkedHashMap<>();

    /**
     * 注册工具提供者到指定角色
     */
    public void register(String role, Object toolProvider) {
        roleTools.computeIfAbsent(role, k -> new ArrayList<>()).add(toolProvider);
        log.debug("ToolRegistry: 注册工具 {} → 角色 {}", toolProvider.getClass().getSimpleName(), role);
    }

    /**
     * 获取指定角色的所有工具提供者
     */
    public List<Object> getToolsForRole(String role) {
        return roleTools.getOrDefault(role, Collections.emptyList());
    }

    /**
     * 获取所有已注册的角色
     */
    public Set<String> getRegisteredRoles() {
        return roleTools.keySet();
    }

    /**
     * 获取工具统计信息
     */
    public Map<String, Integer> getStats() {
        Map<String, Integer> stats = new LinkedHashMap<>();
        for (Map.Entry<String, List<Object>> entry : roleTools.entrySet()) {
            stats.put(entry.getKey(), entry.getValue().size());
        }
        return stats;
    }
}
