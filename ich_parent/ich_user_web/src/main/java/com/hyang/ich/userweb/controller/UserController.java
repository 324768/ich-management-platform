package com.hyang.ich.userweb.controller;

import com.hyang.ich.common.vo.Result;
import com.hyang.ich.user.UserService;
import com.hyang.ich.user.dto.UserAddressDTO;
import com.hyang.ich.user.dto.UserDTO;
import com.hyang.ich.user.dto.UserLoginDTO;
import com.hyang.ich.user.dto.UserRegisterDTO;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/user")
public class UserController {

    @DubboReference(check = false)
    private UserService userService;

    @PostMapping("/register")
    public Result<UserDTO> register(@RequestBody UserRegisterDTO registerDTO) {
        return Result.success(userService.register(registerDTO));
    }

    @PostMapping("/login")
    public Result<String> login(@RequestBody UserLoginDTO loginDTO) {
        return Result.success("登录成功", userService.login(loginDTO));
    }

    @GetMapping("/{id}")
    public Result<UserDTO> get(@PathVariable("id") Long id) {
        return Result.success(userService.findById(id));
    }

    @PutMapping("/update")
    public Result<Void> update(@RequestBody UserDTO userDTO) {
        userService.updateUser(userDTO);
        return Result.success();
    }

    @PutMapping("/password")
    public Result<Void> updatePassword(@RequestParam Long userId,
                                       @RequestParam String oldPassword,
                                       @RequestParam String newPassword) {
        userService.updatePassword(userId, oldPassword, newPassword);
        return Result.success();
    }

    // ========== 收货地址 ==========

    @GetMapping("/address/list")
    public Result<List<UserAddressDTO>> listAddresses(@RequestParam Long userId) {
        return Result.success(userService.listAddresses(userId));
    }

    @PostMapping("/address/add")
    public Result<UserAddressDTO> addAddress(@RequestBody UserAddressDTO addressDTO) {
        return Result.success(userService.addAddress(addressDTO));
    }

    @PutMapping("/address/update")
    public Result<Void> updateAddress(@RequestBody UserAddressDTO addressDTO) {
        userService.updateAddress(addressDTO);
        return Result.success();
    }

    @DeleteMapping("/address/delete")
    public Result<Void> deleteAddress(@RequestParam Long userId, @RequestParam Long addressId) {
        userService.deleteAddress(userId, addressId);
        return Result.success();
    }

    @PutMapping("/address/default")
    public Result<Void> setDefaultAddress(@RequestParam Long userId, @RequestParam Long addressId) {
        userService.setDefaultAddress(userId, addressId);
        return Result.success();
    }
}


