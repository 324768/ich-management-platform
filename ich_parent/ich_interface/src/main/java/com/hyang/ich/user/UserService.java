package com.hyang.ich.user;

import com.hyang.ich.user.dto.UserDTO;

public interface UserService {
    UserDTO findById(Long userId);
}


