package com.hyang.ich.user;

import com.hyang.ich.common.vo.PageResult;
import com.hyang.ich.user.dto.UserAddressDTO;
import com.hyang.ich.user.dto.UserDTO;
import com.hyang.ich.user.dto.UserLoginDTO;
import com.hyang.ich.user.dto.UserRegisterDTO;

import java.util.List;

public interface UserService {

    /** 用户注册 */
    UserDTO register(UserRegisterDTO registerDTO);

    /** 用户登录，返回token */
    String login(UserLoginDTO loginDTO);

    /** 根据ID查询用户 */
    UserDTO findById(Long userId);

    /** 根据用户名查询用户 */
    UserDTO findByUsername(String username);

    /** 更新用户信息 */
    void updateUser(UserDTO userDTO);

    /** 修改密码 */
    void updatePassword(Long userId, String oldPassword, String newPassword);

    /** 分页查询用户列表（管理端） */
    PageResult<UserDTO> listUsers(int pageNum, int pageSize, String keyword);

    /** 启用/禁用用户（管理端） */
    void updateUserStatus(Long userId, Integer status);

    // ========== 收货地址 ==========

    /** 查询用户收货地址列表 */
    List<UserAddressDTO> listAddresses(Long userId);

    /** 新增收货地址 */
    UserAddressDTO addAddress(UserAddressDTO addressDTO);

    /** 修改收货地址 */
    void updateAddress(UserAddressDTO addressDTO);

    /** 删除收货地址 */
    void deleteAddress(Long userId, Long addressId);

    /** 设置默认地址 */
    void setDefaultAddress(Long userId, Long addressId);
}


