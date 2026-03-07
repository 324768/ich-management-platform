package com.hyang.ich.adminweb.controller;

import com.hyang.ich.common.vo.PageResult;
import com.hyang.ich.common.vo.Result;
import com.hyang.ich.user.UserService;
import com.hyang.ich.user.dto.UserDTO;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/user")
public class AdminUserController {

    @DubboReference(check = false)
    private UserService userService;

    @GetMapping("/list")
    public Result<PageResult<UserDTO>> listUsers(@RequestParam(defaultValue = "1") int pageNum,
                                                  @RequestParam(defaultValue = "10") int pageSize,
                                                  @RequestParam(required = false) String keyword) {
        return Result.success(userService.listUsers(pageNum, pageSize, keyword));
    }

    @GetMapping("/{id}")
    public Result<UserDTO> getUser(@PathVariable Long id) {
        return Result.success(userService.findById(id));
    }

    @PutMapping("/status")
    public Result<Void> updateStatus(@RequestParam Long userId, @RequestParam Integer status) {
        userService.updateUserStatus(userId, status);
        return Result.success();
    }
}
