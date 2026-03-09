package com.hyang.ich.user;

import com.hyang.ich.common.vo.PageResult;
import com.hyang.ich.user.dto.UserAddressDTO;
import com.hyang.ich.user.dto.IchUserQualificationDTO;
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

    /** 根据昵称查询用户 */
    UserDTO findByNickname(String nickname);

    /** 删除用户（Ultra AI 专用） */
    void deleteUser(Long userId);

    /** 查询在线用户ID列表 */
    List<Long> listOnlineUserIds();

    /** 查询在线用户详情列表 */
    List<UserDTO> listOnlineUsers();

    /** 设置用户在线状态 */
    void setOnlineStatus(Long userId, boolean online);

    /** 批量设置用户离线（超时清理用） */
    int clearInactiveUsers(int timeoutMinutes);

    /** 统计用户总数 */
    long countUsers();

    // ========== 用户资格认证 ==========

    /** 提交资格认证申请 */
    IchUserQualificationDTO submitQualification(IchUserQualificationDTO dto);

    /** 查询用户自己的资格认证 */
    IchUserQualificationDTO getQualificationByUserId(Long userId);

    /** 分页查询资格认证列表（管理端） */
    PageResult<IchUserQualificationDTO> listQualifications(int pageNum, int pageSize, String keyword, Integer status);

    /** 审核资格认证（管理端） */
    void reviewQualification(Long id, Integer status, String rejectReason, Long reviewerId);

    /** 检查用户是否有传承标志 */
    boolean hasHeritageFlag(Long userId);
}


