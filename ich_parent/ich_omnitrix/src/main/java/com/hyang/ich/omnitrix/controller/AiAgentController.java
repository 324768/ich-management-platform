package com.hyang.ich.omnitrix.controller;

import com.hyang.ich.common.vo.Result;
import com.hyang.ich.omnitrix.agent.tool.ToolRegistry;
import com.hyang.ich.omnitrix.dto.PageVO;
import com.hyang.ich.omnitrix.entity.AiAgentConfig;
import com.hyang.ich.omnitrix.mapper.AiAgentConfigMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/admin/ai/agent")
public class AiAgentController {

    private final AiAgentConfigMapper agentConfigMapper;
    private final ToolRegistry toolRegistry;

    public AiAgentController(AiAgentConfigMapper agentConfigMapper,
                             ToolRegistry toolRegistry) {
        this.agentConfigMapper = agentConfigMapper;
        this.toolRegistry = toolRegistry;
    }

    /**
     * 分页查询代理配置
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
     * 获取代理详情
     */
    @GetMapping("/{agentCode}")
    public Result<AiAgentConfig> getByCode(@PathVariable String agentCode) {
        AiAgentConfig config = agentConfigMapper.selectByCode(agentCode);
        if (config == null) {
            return Result.failed("代理不存在");
        }
        return Result.success(config);
    }

    /**
     * 新增/修改代理配置
     */
    @PostMapping("/save")
    public Result<Void> save(@RequestBody AiAgentConfig config) {
        if (config.getStatus() == null) {
            config.setStatus(1);
        }

        if (config.getId() != null) {
            agentConfigMapper.update(config);
            log.info("更新代理配置: [{}] {}", config.getAgentCode(), config.getAgentName());
        } else {
            config.setVersion(1L);
            if (config.getSort() == null) config.setSort(0);
            if (config.getFallbackStrategy() == null) config.setFallbackStrategy("general");
            agentConfigMapper.insert(config);
            log.info("新增代理配置: [{}] {}", config.getAgentCode(), config.getAgentName());
        }
        return Result.success();
    }

    /**
     * 删除代理配置
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        agentConfigMapper.deleteById(id);
        return Result.success();
    }

    /**
     * 修改状态（启用/停用）
     */
    @PutMapping("/changeStatus")
    public Result<Void> changeStatus(@RequestBody AiAgentConfig config) {
        agentConfigMapper.updateStatus(config.getId(), config.getStatus());
        return Result.success();
    }

    /**
     * 查询当前已注册的 Tool 统计（按角色分组）
     */
    @GetMapping("/registered")
    public Result<Map<String, Integer>> registeredTools() {
        return Result.success(toolRegistry.getStats());
    }
}
