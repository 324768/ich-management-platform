package com.hyang.ich.omnitrix.controller;

import com.hyang.ich.common.vo.Result;
import com.hyang.ich.omnitrix.agent.SubAgentRegistry;
import com.hyang.ich.omnitrix.dto.PageVO;
import com.hyang.ich.omnitrix.entity.AiAgentConfig;
import com.hyang.ich.omnitrix.mapper.AiAgentConfigMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@Slf4j
@RestController
@RequestMapping("/api/admin/ai/agent")
public class AiAgentController {

    private final AiAgentConfigMapper agentConfigMapper;
    private final SubAgentRegistry subAgentRegistry;

    public AiAgentController(AiAgentConfigMapper agentConfigMapper,
                             SubAgentRegistry subAgentRegistry) {
        this.agentConfigMapper = agentConfigMapper;
        this.subAgentRegistry = subAgentRegistry;
    }

    /**
     * 分页查询子代理配置
     */
    @GetMapping("/list")
    public Result<PageVO<AiAgentConfig>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword) {
        int offset = (pageNum - 1) * pageSize;
        List<AiAgentConfig> list = agentConfigMapper.selectPage(offset, pageSize, keyword);
        int total = agentConfigMapper.countAll(keyword);
        return Result.success(PageVO.of(list, total));
    }

    /**
     * 获取子代理详情
     */
    @GetMapping("/{agentCode}")
    public Result<AiAgentConfig> getByCode(@PathVariable String agentCode) {
        AiAgentConfig config = agentConfigMapper.selectByCode(agentCode);
        if (config == null) {
            return Result.failed("子代理不存在");
        }
        return Result.success(config);
    }

    /**
     * 新增子代理
     */
    @PostMapping("/save")
    public Result<Void> save(@RequestBody AiAgentConfig config) {
        // 校验 agentCode 不与系统代理冲突
        Set<String> systemCodes = subAgentRegistry.getAllCodes();
        if (config.getId() == null && systemCodes.contains(config.getAgentCode())) {
            // 检查是否是系统代理（系统代理不在 dynamicAgents 中）
            if (subAgentRegistry.get(config.getAgentCode()) != null
                    && !(subAgentRegistry.get(config.getAgentCode()) instanceof com.hyang.ich.omnitrix.agent.impl.DynamicSubAgent)) {
                return Result.failed("代理编码 [" + config.getAgentCode() + "] 与系统内置代理冲突");
            }
        }

        if (config.getStatus() == null) {
            config.setStatus(1);
        }

        if (config.getId() != null) {
            // 修改（version 在 SQL 中自增）
            agentConfigMapper.update(config);
            log.info("更新动态代理: [{}] {}", config.getAgentCode(), config.getAgentName());
        } else {
            // 新增
            config.setVersion(1L);
            if (config.getSort() == null) config.setSort(0);
            if (config.getFallbackStrategy() == null) config.setFallbackStrategy("general");
            agentConfigMapper.insert(config);
            log.info("新增动态代理: [{}] {}", config.getAgentCode(), config.getAgentName());
        }

        // 刷新动态代理缓存
        subAgentRegistry.refreshDynamicAgents();
        return Result.success();
    }

    /**
     * 删除子代理
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        agentConfigMapper.deleteById(id);
        subAgentRegistry.refreshDynamicAgents();
        return Result.success();
    }

    /**
     * 修改状态（启用/停用）
     */
    @PutMapping("/changeStatus")
    public Result<Void> changeStatus(@RequestBody AiAgentConfig config) {
        agentConfigMapper.updateStatus(config.getId(), config.getStatus());
        subAgentRegistry.refreshDynamicAgents();
        return Result.success();
    }

    /**
     * 查询当前所有已注册的代理（系统 + 动态）
     */
    @GetMapping("/registered")
    public Result<Set<String>> registeredAgents() {
        return Result.success(subAgentRegistry.getAllCodes());
    }
}
