package com.hyang.ich.adminweb.controller;

import com.hyang.ich.common.utils.JwtUtils;
import com.hyang.ich.common.vo.Result;
import com.hyang.ich.system.SystemService;
import com.hyang.ich.system.dto.SysLoginDTO;
import com.hyang.ich.system.dto.SysUserDTO;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/admin/auth")
public class AdminLoginController {

    @DubboReference(check = false)
    private SystemService systemService;

    @PostMapping("/login")
    public Result<String> login(@RequestBody SysLoginDTO loginDTO) {
        String token = systemService.login(loginDTO);
        return Result.success("登录成功", token);
    }

    @GetMapping("/info")
    public Result<SysUserDTO> getAdminInfo(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        Long adminId = JwtUtils.getUserId(token);
        return Result.success(systemService.getAdminInfo(adminId));
    }
}
