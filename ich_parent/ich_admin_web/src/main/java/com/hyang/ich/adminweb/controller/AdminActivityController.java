package com.hyang.ich.adminweb.controller;

import com.hyang.ich.common.vo.PageResult;
import com.hyang.ich.common.vo.Result;
import com.hyang.ich.content.ContentService;
import com.hyang.ich.content.dto.IchActivityCommentDTO;
import com.hyang.ich.content.dto.IchActivityDTO;
import com.hyang.ich.content.dto.IchActivityRecordDTO;
import com.hyang.ich.content.dto.IchActivityViewLogDTO;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/activity")
public class AdminActivityController {

    @DubboReference(check = false)
    private ContentService contentService;

    @GetMapping("/list")
    public Result<PageResult<IchActivityDTO>> listActivities(@RequestParam(defaultValue = "1") int pageNum,
                                                             @RequestParam(defaultValue = "10") int pageSize,
                                                             @RequestParam(required = false) String keyword,
                                                             @RequestParam(required = false) Integer status,
                                                             @RequestParam(required = false) Integer activityType) {
        return Result.success(contentService.listActivities(pageNum, pageSize, keyword, status, activityType));
    }

    @GetMapping("/{id}")
    public Result<IchActivityDTO> getActivity(@PathVariable Long id) {
        return Result.success(contentService.getActivityById(id));
    }

    @PostMapping("/add")
    public Result<IchActivityDTO> addActivity(@RequestBody IchActivityDTO dto) {
        return Result.success(contentService.addActivity(dto));
    }

    @PutMapping("/update")
    public Result<Void> updateActivity(@RequestBody IchActivityDTO dto) {
        contentService.updateActivity(dto);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteActivity(@PathVariable Long id) {
        contentService.deleteActivity(id);
        return Result.success();
    }

    @PutMapping("/status")
    public Result<Void> updateActivityStatus(@RequestParam Long id, @RequestParam Integer status) {
        contentService.updateActivityStatus(id, status);
        return Result.success();
    }

    @GetMapping("/record/list")
    public Result<PageResult<IchActivityRecordDTO>> listActivityRecords(@RequestParam(defaultValue = "1") int pageNum,
                                                                        @RequestParam(defaultValue = "10") int pageSize,
                                                                        @RequestParam(required = false) String keyword,
                                                                        @RequestParam(required = false) Integer status,
                                                                        @RequestParam(required = false) Long activityId) {
        return Result.success(contentService.listActivityRecords(pageNum, pageSize, keyword, status, activityId));
    }

    @PutMapping("/record/status")
    public Result<Void> updateActivityRecordStatus(@RequestParam Long id, @RequestParam Integer status) {
        contentService.updateActivityRecordStatus(id, status);
        return Result.success();
    }

    @DeleteMapping("/record/{id}")
    public Result<Void> deleteActivityRecord(@PathVariable Long id) {
        contentService.deleteActivityRecord(id);
        return Result.success();
    }

    // ========== 活动审批 ==========

    @GetMapping("/approval/list")
    public Result<PageResult<IchActivityDTO>> listApprovalActivities(@RequestParam(defaultValue = "1") int pageNum,
                                                                      @RequestParam(defaultValue = "10") int pageSize,
                                                                      @RequestParam(required = false) String keyword,
                                                                      @RequestParam Integer approvalStatus) {
        return Result.success(contentService.listActivitiesByApproval(pageNum, pageSize, keyword, approvalStatus));
    }

    @PutMapping("/approval/review")
    public Result<Void> reviewActivity(@RequestParam Long id,
                                        @RequestParam Integer approvalStatus,
                                        @RequestParam(required = false) String rejectReason,
                                        @RequestParam(required = false) Long reviewerId) {
        contentService.updateActivityApprovalStatus(id, approvalStatus, rejectReason, reviewerId);
        return Result.success();
    }

    // ========== 活动评论 ==========

    @GetMapping("/comment/list")
    public Result<PageResult<IchActivityCommentDTO>> listComments(@RequestParam Long activityId,
                                                                   @RequestParam(defaultValue = "1") int pageNum,
                                                                   @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(contentService.listActivityComments(activityId, pageNum, pageSize));
    }

    @PostMapping("/comment/add")
    public Result<IchActivityCommentDTO> addComment(@RequestBody IchActivityCommentDTO dto) {
        return Result.success(contentService.addActivityComment(dto));
    }

    @DeleteMapping("/comment/{id}")
    public Result<Void> deleteComment(@PathVariable Long id) {
        contentService.deleteActivityComment(id);
        return Result.success();
    }

    @PutMapping("/comment/status")
    public Result<Void> updateCommentStatus(@RequestParam Long id, @RequestParam Integer status) {
        contentService.updateActivityCommentStatus(id, status);
        return Result.success();
    }

    // ========== 活动浏览记录 ==========

    @GetMapping("/viewlog/list")
    public Result<PageResult<IchActivityViewLogDTO>> listViewLogs(@RequestParam Long activityId,
                                                                    @RequestParam(defaultValue = "1") int pageNum,
                                                                    @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(contentService.listActivityViewLogs(activityId, pageNum, pageSize));
    }

    @PostMapping("/viewlog/add")
    public Result<Void> addViewLog(@RequestBody IchActivityViewLogDTO dto) {
        contentService.addActivityViewLog(dto);
        return Result.success();
    }
}
