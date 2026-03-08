package com.hyang.ich.adminweb.controller;

import com.hyang.ich.common.vo.PageResult;
import com.hyang.ich.common.vo.Result;
import com.hyang.ich.user.UserService;
import com.hyang.ich.user.dto.IchUserQualificationDTO;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/qualification")
public class AdminQualificationController {

    @DubboReference(check = false)
    private UserService userService;

    @GetMapping("/list")
    public Result<PageResult<IchUserQualificationDTO>> list(@RequestParam(defaultValue = "1") int pageNum,
                                                             @RequestParam(defaultValue = "10") int pageSize,
                                                             @RequestParam(required = false) String keyword,
                                                             @RequestParam(required = false) Integer status) {
        return Result.success(userService.listQualifications(pageNum, pageSize, keyword, status));
    }

    @PutMapping("/review")
    public Result<Void> review(@RequestParam Long id,
                                @RequestParam Integer status,
                                @RequestParam(required = false) String rejectReason,
                                @RequestParam(required = false) Long reviewerId) {
        userService.reviewQualification(id, status, rejectReason, reviewerId);
        return Result.success();
    }

    @GetMapping("/check/{userId}")
    public Result<Boolean> checkHeritage(@PathVariable Long userId) {
        return Result.success(userService.hasHeritageFlag(userId));
    }
}
