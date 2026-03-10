package com.hyang.ich.omnitrix.controller;

import com.hyang.ich.common.vo.Result;
import com.hyang.ich.omnitrix.dto.PageVO;
import com.hyang.ich.omnitrix.entity.AiKnowledgeBase;
import com.hyang.ich.omnitrix.entity.AiPromptConfig;
import com.hyang.ich.omnitrix.infrastructure.prompt.PromptManager;
import com.hyang.ich.omnitrix.mapper.AiPromptConfigMapper;
import com.hyang.ich.omnitrix.service.KnowledgeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/admin/ai/config")
public class AiConfigController {

    private final KnowledgeService knowledgeService;
    private final AiPromptConfigMapper promptConfigMapper;
    private final PromptManager promptManager;

    public AiConfigController(KnowledgeService knowledgeService,
                              AiPromptConfigMapper promptConfigMapper,
                              PromptManager promptManager) {
        this.knowledgeService = knowledgeService;
        this.promptConfigMapper = promptConfigMapper;
        this.promptManager = promptManager;
    }

    // ========== 知识库管理 ==========

    @GetMapping("/knowledge/list")
    public Result<PageVO<AiKnowledgeBase>> listKnowledge(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword) {
        List<AiKnowledgeBase> list = knowledgeService.listPage(pageNum, pageSize, keyword);
        int total = knowledgeService.countAll(keyword);
        return Result.success(PageVO.of(list, total));
    }

    @PostMapping("/knowledge/save")
    public Result<Void> saveKnowledge(@RequestBody AiKnowledgeBase kb) {
        if (kb.getStatus() == null) {
            kb.setStatus(1);
        }
        if (kb.getHitCount() == null) {
            kb.setHitCount(0);
        }
        knowledgeService.save(kb);
        return Result.success();
    }

    @DeleteMapping("/knowledge/{id}")
    public Result<Void> deleteKnowledge(@PathVariable Long id) {
        knowledgeService.deleteById(id);
        return Result.success();
    }

    // ========== Prompt 配置管理 ==========

    @GetMapping("/prompt/list")
    public Result<List<AiPromptConfig>> listPrompt() {
        return Result.success(promptConfigMapper.selectAll());
    }

    @PostMapping("/prompt/save")
    public Result<Void> savePrompt(@RequestBody AiPromptConfig config) {
        if (config.getStatus() == null) {
            config.setStatus(1);
        }
        if (config.getId() != null) {
            promptConfigMapper.update(config);
        } else {
            // 新建时自动分配版本号
            Integer maxVersion = promptConfigMapper.selectMaxVersion(config.getPromptKey());
            config.setVersion(maxVersion != null ? maxVersion + 1 : 1);
            if (config.getIsActive() == null) {
                config.setIsActive(1);
            }
            // 新版本激活时，先停用旧版本
            if (Integer.valueOf(1).equals(config.getIsActive())) {
                promptConfigMapper.deactivateAllVersions(config.getPromptKey());
            }
            promptConfigMapper.insert(config);
        }
        // 主动失效 Prompt 缓存
        promptManager.invalidateCache(config.getPromptKey());
        return Result.success();
    }

    @DeleteMapping("/prompt/{id}")
    public Result<Void> deletePrompt(@PathVariable Long id) {
        promptConfigMapper.deleteById(id);
        return Result.success();
    }

    /** 查看某个 promptKey 的所有版本历史 */
    @GetMapping("/prompt/versions")
    public Result<List<AiPromptConfig>> listPromptVersions(@RequestParam String promptKey) {
        return Result.success(promptConfigMapper.selectVersionsByKey(promptKey));
    }

    /** 激活指定版本（停用同 key 其他版本） */
    @PostMapping("/prompt/activate/{id}")
    public Result<Void> activatePromptVersion(@PathVariable Long id,
                                               @RequestParam String promptKey) {
        promptConfigMapper.deactivateAllVersions(promptKey);
        promptConfigMapper.activateVersion(id);
        promptManager.invalidateCache(promptKey);
        return Result.success();
    }
}
