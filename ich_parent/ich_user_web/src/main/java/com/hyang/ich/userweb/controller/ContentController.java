package com.hyang.ich.userweb.controller;

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
@RequestMapping("/api/content")
public class ContentController {

    @DubboReference(check = false)
    private ContentService contentService;

    @GetMapping("/category/tree")
    public Result<List<IchCategoryDTO>> listCategoryTree() {
        return Result.success(contentService.listCategoryTree());
    }

    @GetMapping("/category/list")
    public Result<List<IchCategoryDTO>> listCategories(@RequestParam(defaultValue = "0") Long parentId) {
        return Result.success(contentService.listCategories(parentId));
    }

    @GetMapping("/item/list")
    public Result<PageResult<IchItemDTO>> listItems(@RequestParam(defaultValue = "1") int pageNum,
                                                     @RequestParam(defaultValue = "10") int pageSize,
                                                     @RequestParam(required = false) Long categoryId,
                                                     @RequestParam(required = false) String keyword) {
        return Result.success(contentService.listItems(pageNum, pageSize, categoryId, keyword, 1));
    }

    @GetMapping("/item/{id}")
    public Result<IchItemDTO> getItem(@PathVariable Long id) {
        return Result.success(contentService.getItemById(id));
    }

    @GetMapping("/heritage/list")
    public Result<PageResult<IchHeritageManDTO>> listHeritageMan(@RequestParam(defaultValue = "1") int pageNum,
                                                                  @RequestParam(defaultValue = "10") int pageSize,
                                                                  @RequestParam(required = false) String keyword) {
        return Result.success(contentService.listHeritageMan(pageNum, pageSize, keyword, 1));
    }

    @GetMapping("/heritage/{id}")
    public Result<IchHeritageManDTO> getHeritageMan(@PathVariable Long id) {
        return Result.success(contentService.getHeritageManById(id));
    }
}
