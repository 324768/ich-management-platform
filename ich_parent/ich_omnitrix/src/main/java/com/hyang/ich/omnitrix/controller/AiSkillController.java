package com.hyang.ich.omnitrix.controller;

import com.hyang.ich.omnitrix.entity.AiSkillConfig;
import com.hyang.ich.omnitrix.service.SkillConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI技能管理Controller
 * 提供Skill的增删改查及启用/禁用接口
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/ai/skills")
public class AiSkillController {

    private final SkillConfigService skillConfigService;

    public AiSkillController(SkillConfigService skillConfigService) {
        this.skillConfigService = skillConfigService;
    }

    /**
     * 获取所有Skills（含启用状态）
     */
    @GetMapping("/list")
    public ResponseEntity<Map<String, Object>> getAllSkills() {
        List<AiSkillConfig> skills = skillConfigService.getAllSkills();
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", skills);
        result.put("total", skills.size());
        return ResponseEntity.ok(result);
    }

    /**
     * 获取所有启用的Skills
     */
    @GetMapping("/enabled")
    public ResponseEntity<Map<String, Object>> getEnabledSkills() {
        List<AiSkillConfig> skills = skillConfigService.getEnabledSkills();
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", skills);
        return ResponseEntity.ok(result);
    }

    /**
     * 分页查询Skills
     */
    @GetMapping("/page")
    public ResponseEntity<Map<String, Object>> getSkillsPage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword) {
        List<AiSkillConfig> skills = skillConfigService.getSkillsPage(page, pageSize, keyword);
        int total = skillConfigService.countSkills(keyword);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("data", skills);
        result.put("total", total);
        result.put("page", page);
        result.put("pageSize", pageSize);
        return ResponseEntity.ok(result);
    }

    /**
     * 获取单个Skill详情
     */
    @GetMapping("/{skillId}")
    public ResponseEntity<Map<String, Object>> getSkillById(@PathVariable String skillId) {
        AiSkillConfig skill = skillConfigService.getSkillById(skillId);
        Map<String, Object> result = new HashMap<>();
        if (skill != null) {
            result.put("code", 200);
            result.put("data", skill);
        } else {
            result.put("code", 404);
            result.put("message", "Skill不存在");
        }
        return ResponseEntity.ok(result);
    }

    /**
     * 启用指定Skill
     */
    @PostMapping("/{skillId}/enable")
    public ResponseEntity<Map<String, Object>> enableSkill(@PathVariable String skillId) {
        boolean success = skillConfigService.enableSkill(skillId);
        Map<String, Object> result = new HashMap<>();
        if (success) {
            result.put("code", 200);
            result.put("message", "Skill已启用");
        } else {
            result.put("code", 500);
            result.put("message", "启用失败");
        }
        return ResponseEntity.ok(result);
    }

    /**
     * 禁用指定Skill
     */
    @PostMapping("/{skillId}/disable")
    public ResponseEntity<Map<String, Object>> disableSkill(@PathVariable String skillId) {
        boolean success = skillConfigService.disableSkill(skillId);
        Map<String, Object> result = new HashMap<>();
        if (success) {
            result.put("code", 200);
            result.put("message", "Skill已禁用");
        } else {
            result.put("code", 500);
            result.put("message", "禁用失败");
        }
        return ResponseEntity.ok(result);
    }

    /**
     * 批量启用Skills
     */
    @PostMapping("/batch/enable")
    public ResponseEntity<Map<String, Object>> batchEnableSkills(@RequestBody List<String> skillIds) {
        int count = skillConfigService.batchUpdateEnabled(skillIds, 1);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "成功启用 " + count + " 个Skills");
        result.put("count", count);
        return ResponseEntity.ok(result);
    }

    /**
     * 批量禁用Skills
     */
    @PostMapping("/batch/disable")
    public ResponseEntity<Map<String, Object>> batchDisableSkills(@RequestBody List<String> skillIds) {
        int count = skillConfigService.batchUpdateEnabled(skillIds, 0);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "成功禁用 " + count + " 个Skills");
        result.put("count", count);
        return ResponseEntity.ok(result);
    }

    /**
     * 批量设置Skills启用状态
     */
    @PostMapping("/batch/status")
    public ResponseEntity<Map<String, Object>> batchSetStatus(@RequestBody Map<String, Object> request) {
        List<String> skillIds = (List<String>) request.get("skillIds");
        Integer enabled = (Integer) request.get("enabled");

        if (skillIds == null || skillIds.isEmpty() || enabled == null) {
            Map<String, Object> result = new HashMap<>();
            result.put("code", 400);
            result.put("message", "参数错误");
            return ResponseEntity.badRequest().body(result);
        }

        int count = skillConfigService.batchUpdateEnabled(skillIds, enabled);
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", "成功更新 " + count + " 个Skills");
        result.put("count", count);
        return ResponseEntity.ok(result);
    }
}
