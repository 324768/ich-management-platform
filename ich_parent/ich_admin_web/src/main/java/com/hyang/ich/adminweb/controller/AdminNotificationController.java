package com.hyang.ich.adminweb.controller;

import com.hyang.ich.common.vo.PageResult;
import com.hyang.ich.common.vo.Result;
import com.hyang.ich.system.SystemService;
import com.hyang.ich.system.dto.SysNotificationDTO;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/notification")
public class AdminNotificationController {

    @DubboReference(check = false)
    private SystemService systemService;

    @GetMapping("/list")
    public Result<PageResult<SysNotificationDTO>> listNotifications(@RequestParam(defaultValue = "1") int pageNum,
                                                                    @RequestParam(defaultValue = "10") int pageSize,
                                                                    @RequestParam(required = false) String keyword,
                                                                    @RequestParam(required = false) Integer messageType,
                                                                    @RequestParam(required = false) Integer isPublished) {
        return Result.success(systemService.listNotifications(pageNum, pageSize, keyword, messageType, isPublished));
    }

    @GetMapping("/{id}")
    public Result<SysNotificationDTO> getNotification(@PathVariable Long id) {
        return Result.success(systemService.getNotificationById(id));
    }

    @PostMapping("/add")
    public Result<SysNotificationDTO> addNotification(@RequestBody SysNotificationDTO dto) {
        return Result.success(systemService.addNotification(dto));
    }

    @PutMapping("/update")
    public Result<Void> updateNotification(@RequestBody SysNotificationDTO dto) {
        systemService.updateNotification(dto);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteNotification(@PathVariable Long id) {
        systemService.deleteNotification(id);
        return Result.success();
    }

    @PutMapping("/publish/{id}")
    public Result<Void> publishNotification(@PathVariable Long id) {
        systemService.publishNotification(id);
        return Result.success();
    }
}
