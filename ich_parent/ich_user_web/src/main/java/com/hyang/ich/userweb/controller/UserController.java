package com.hyang.ich.userweb.controller;

import com.hyang.ich.user.UserService;
import com.hyang.ich.user.dto.UserDTO;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
public class UserController {

    @DubboReference(check = false)
    private UserService userService;

    @GetMapping("/{id}")
    public UserDTO get(@PathVariable("id") Long id) {
        return userService.findById(id);
    }
}


