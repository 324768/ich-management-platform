package com.hyang.ich.adminweb.controller;

import com.hyang.ich.common.vo.PageResult;
import com.hyang.ich.common.vo.Result;
import com.hyang.ich.content.ContentService;
import com.hyang.ich.content.dto.IchCategoryDTO;
import com.hyang.ich.content.dto.IchHeritageManDTO;
import com.hyang.ich.content.dto.IchItemDTO;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/content")
public class AdminContentController {

    @DubboReference(check = false)
    private ContentService contentService;

    // ========== 分类管理 ==========

    @GetMapping("/category/tree")
    public Result<List<IchCategoryDTO>> listCategoryTree() {
        return Result.success(contentService.listCategoryTree());
    }

    @GetMapping("/category/list")
    public Result<List<IchCategoryDTO>> listCategories(@RequestParam(defaultValue = "0") Long parentId) {
        return Result.success(contentService.listCategories(parentId));
    }

    @GetMapping("/category/{id}")
    public Result<IchCategoryDTO> getCategory(@PathVariable Long id) {
        return Result.success(contentService.getCategoryById(id));
    }

    @PostMapping("/category/add")
    public Result<IchCategoryDTO> addCategory(@RequestBody IchCategoryDTO dto) {
        return Result.success(contentService.addCategory(dto));
    }

    @PutMapping("/category/update")
    public Result<Void> updateCategory(@RequestBody IchCategoryDTO dto) {
        contentService.updateCategory(dto);
        return Result.success();
    }

    @DeleteMapping("/category/{id}")
    public Result<Void> deleteCategory(@PathVariable Long id) {
        contentService.deleteCategory(id);
        return Result.success();
    }

    // ========== 非遗项目管理 ==========

    @GetMapping("/item/list")
    public Result<PageResult<IchItemDTO>> listItems(@RequestParam(defaultValue = "1") int pageNum,
                                                     @RequestParam(defaultValue = "10") int pageSize,
                                                     @RequestParam(required = false) Long categoryId,
                                                     @RequestParam(required = false) String keyword,
                                                     @RequestParam(required = false) Integer status) {
        return Result.success(contentService.listItems(pageNum, pageSize, categoryId, keyword, status));
    }

    @GetMapping("/item/{id}")
    public Result<IchItemDTO> getItem(@PathVariable Long id) {
        return Result.success(contentService.getItemById(id));
    }

    @PostMapping("/item/add")
    public Result<IchItemDTO> addItem(@RequestBody IchItemDTO dto) {
        return Result.success(contentService.addItem(dto));
    }

    @PutMapping("/item/update")
    public Result<Void> updateItem(@RequestBody IchItemDTO dto) {
        contentService.updateItem(dto);
        return Result.success();
    }

    @DeleteMapping("/item/{id}")
    public Result<Void> deleteItem(@PathVariable Long id) {
        contentService.deleteItem(id);
        return Result.success();
    }

    @PutMapping("/item/status")
    public Result<Void> updateItemStatus(@RequestParam Long id, @RequestParam Integer status) {
        contentService.updateItemStatus(id, status);
        return Result.success();
    }

    // ========== 传承人管理 ==========

    @GetMapping("/heritage/list")
    public Result<PageResult<IchHeritageManDTO>> listHeritageMan(@RequestParam(defaultValue = "1") int pageNum,
                                                                  @RequestParam(defaultValue = "10") int pageSize,
                                                                  @RequestParam(required = false) String keyword,
                                                                  @RequestParam(required = false) Integer status) {
        return Result.success(contentService.listHeritageMan(pageNum, pageSize, keyword, status));
    }

    @GetMapping("/heritage/{id}")
    public Result<IchHeritageManDTO> getHeritageMan(@PathVariable Long id) {
        return Result.success(contentService.getHeritageManById(id));
    }

    @PostMapping("/heritage/add")
    public Result<IchHeritageManDTO> addHeritageMan(@RequestBody IchHeritageManDTO dto) {
        return Result.success(contentService.addHeritageMan(dto));
    }

    @PutMapping("/heritage/update")
    public Result<Void> updateHeritageMan(@RequestBody IchHeritageManDTO dto) {
        contentService.updateHeritageMan(dto);
        return Result.success();
    }

    @DeleteMapping("/heritage/{id}")
    public Result<Void> deleteHeritageMan(@PathVariable Long id) {
        contentService.deleteHeritageMan(id);
        return Result.success();
    }
}
