package com.hyang.ich.user.service.impl;

import com.hyang.ich.user.UserService;
import com.hyang.ich.user.dto.UserDTO;
import org.apache.dubbo.config.annotation.DubboService;

@DubboService
public class UserServiceImpl implements UserService {
    @Override
    public UserDTO findById(Long userId) {
        UserDTO dto = new UserDTO();
        dto.setId(userId);
        dto.setUsername("user-" + userId);
        return dto;
    }
}


