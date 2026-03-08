package com.hyang.ich.userweb.controller;

import com.hyang.ich.common.vo.PageResult;
import com.hyang.ich.common.vo.Result;
import com.hyang.ich.content.ContentService;
import com.hyang.ich.content.dto.IchActivityCommentDTO;
import com.hyang.ich.content.dto.IchActivityDTO;
import com.hyang.ich.content.dto.IchActivityRecordDTO;
import com.hyang.ich.content.dto.IchActivityViewLogDTO;
import com.hyang.ich.content.dto.IchCategoryDTO;
import com.hyang.ich.content.dto.IchHeritageManDTO;
import com.hyang.ich.content.dto.IchItemDTO;
import com.hyang.ich.content.dto.IchPostDTO;
import com.hyang.ich.content.dto.IchPostCommentDTO;
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

    // ========== 活动 ==========

    @GetMapping("/activity/list")
    public Result<PageResult<IchActivityDTO>> listActivities(@RequestParam(defaultValue = "1") int pageNum,
                                                              @RequestParam(defaultValue = "10") int pageSize,
                                                              @RequestParam(required = false) String keyword,
                                                              @RequestParam(required = false) Integer activityType) {
        return Result.success(contentService.listActivities(pageNum, pageSize, keyword, null, activityType));
    }

    @GetMapping("/activity/{id}")
    public Result<IchActivityDTO> getActivity(@PathVariable Long id) {
        return Result.success(contentService.getActivityById(id));
    }

    @PostMapping("/activity/register")
    public Result<IchActivityRecordDTO> registerActivity(@RequestBody IchActivityRecordDTO dto) {
        IchActivityRecordDTO result = contentService.registerActivity(dto);
        if (result == null) {
            return Result.failed("报名已满或活动不存在");
        }
        return Result.success(result);
    }

    // ========== 活动评论 ==========

    @GetMapping("/activity/comment/list")
    public Result<PageResult<IchActivityCommentDTO>> listComments(@RequestParam Long activityId,
                                                                   @RequestParam(defaultValue = "1") int pageNum,
                                                                   @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(contentService.listActivityComments(activityId, pageNum, pageSize));
    }

    @PostMapping("/activity/comment/add")
    public Result<IchActivityCommentDTO> addComment(@RequestBody IchActivityCommentDTO dto) {
        return Result.success(contentService.addActivityComment(dto));
    }

    // ========== 活动浏览记录 ==========

    @PostMapping("/activity/viewlog/add")
    public Result<Void> addViewLog(@RequestBody IchActivityViewLogDTO dto) {
        contentService.addActivityViewLog(dto);
        return Result.success();
    }

    // ========== 非遗动态/笔记 ==========

    @GetMapping("/post/list")
    public Result<PageResult<IchPostDTO>> listPosts(@RequestParam(defaultValue = "1") int pageNum,
                                                      @RequestParam(defaultValue = "20") int pageSize,
                                                      @RequestParam(required = false) String keyword,
                                                      @RequestParam(required = false) Integer type) {
        return Result.success(contentService.listPosts(pageNum, pageSize, keyword, type));
    }

    @GetMapping("/post/{id}")
    public Result<IchPostDTO> getPost(@PathVariable Long id) {
        return Result.success(contentService.getPostById(id));
    }

    @PostMapping("/post/add")
    public Result<IchPostDTO> addPost(@RequestBody IchPostDTO dto) {
        return Result.success(contentService.addPost(dto));
    }

    @PostMapping("/post/like")
    public Result<Void> likePost(@RequestParam Long postId, @RequestParam Long userId) {
        contentService.likePost(postId, userId);
        return Result.success();
    }

    @PostMapping("/post/unlike")
    public Result<Void> unlikePost(@RequestParam Long postId, @RequestParam Long userId) {
        contentService.unlikePost(postId, userId);
        return Result.success();
    }

    @PostMapping("/post/favorite")
    public Result<Void> favoritePost(@RequestParam Long postId, @RequestParam Long userId) {
        contentService.favoritePost(postId, userId);
        return Result.success();
    }

    @PostMapping("/post/unfavorite")
    public Result<Void> unfavoritePost(@RequestParam Long postId, @RequestParam Long userId) {
        contentService.unfavoritePost(postId, userId);
        return Result.success();
    }

    @GetMapping("/post/hasLiked")
    public Result<Boolean> hasLikedPost(@RequestParam Long postId, @RequestParam Long userId) {
        return Result.success(contentService.hasLikedPost(postId, userId));
    }

    @GetMapping("/post/hasFavorited")
    public Result<Boolean> hasFavoritedPost(@RequestParam Long postId, @RequestParam Long userId) {
        return Result.success(contentService.hasFavoritedPost(postId, userId));
    }

    @GetMapping("/post/user/posts")
    public Result<PageResult<IchPostDTO>> listUserPosts(@RequestParam Long userId,
                                                         @RequestParam(defaultValue = "1") int pageNum,
                                                         @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(contentService.listUserPosts(userId, pageNum, pageSize));
    }

    @GetMapping("/post/user/liked")
    public Result<PageResult<IchPostDTO>> listUserLikedPosts(@RequestParam Long userId,
                                                              @RequestParam(defaultValue = "1") int pageNum,
                                                              @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(contentService.listUserLikedPosts(userId, pageNum, pageSize));
    }

    @GetMapping("/post/user/favorited")
    public Result<PageResult<IchPostDTO>> listUserFavoritedPosts(@RequestParam Long userId,
                                                                   @RequestParam(defaultValue = "1") int pageNum,
                                                                   @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(contentService.listUserFavoritedPosts(userId, pageNum, pageSize));
    }

    // ========== 非遗动态评论 ==========

    @GetMapping("/post/comment/list")
    public Result<PageResult<IchPostCommentDTO>> listPostComments(@RequestParam Long postId,
                                                                    @RequestParam(defaultValue = "1") int pageNum,
                                                                    @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(contentService.listPostComments(postId, pageNum, pageSize));
    }

    @PostMapping("/post/comment/add")
    public Result<IchPostCommentDTO> addPostComment(@RequestBody IchPostCommentDTO dto) {
        return Result.success(contentService.addPostComment(dto));
    }
}
