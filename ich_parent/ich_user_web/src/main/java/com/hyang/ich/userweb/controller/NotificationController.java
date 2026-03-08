package com.hyang.ich.userweb.controller;

import com.hyang.ich.common.vo.PageResult;
import com.hyang.ich.common.vo.Result;
import com.hyang.ich.system.SystemService;
import com.hyang.ich.system.dto.SysNotificationDTO;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notification")
public class NotificationController {

    @DubboReference(check = false)
    private SystemService systemService;

    @GetMapping("/list")
    public Result<PageResult<SysNotificationDTO>> list(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        // 用户端只查看已发布的通知
        return Result.success(systemService.listNotifications(pageNum, pageSize, null, null, 1));
    }

    @GetMapping("/{id}")
    public Result<SysNotificationDTO> get(@PathVariable Long id) {
        return Result.success(systemService.getNotificationById(id));
    }
}
