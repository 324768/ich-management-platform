package com.hyang.ich.system;

import com.hyang.ich.common.vo.PageResult;
import com.hyang.ich.system.dto.SysLoginDTO;
import com.hyang.ich.system.dto.SysNotificationDTO;
import com.hyang.ich.system.dto.SysOperationLogDTO;
import com.hyang.ich.system.dto.SysRoleDTO;
import com.hyang.ich.system.dto.SysUserDTO;

import java.util.List;

public interface SystemService {

    // ========== 管理员登录 ==========

    /** 管理员登录，返回token */
    String login(SysLoginDTO loginDTO);

    /** 获取当前管理员信息 */
    SysUserDTO getAdminInfo(Long adminId);

    // ========== 管理员用户管理 ==========

    /** 分页查询管理员列表 */
    PageResult<SysUserDTO> listAdmins(int pageNum, int pageSize, String keyword);

    /** 新增管理员 */
    SysUserDTO addAdmin(SysUserDTO sysUserDTO, String password);

    /** 修改管理员 */
    void updateAdmin(SysUserDTO sysUserDTO);

    /** 删除管理员 */
    void deleteAdmin(Long adminId);

    /** 启用/禁用管理员 */
    void updateAdminStatus(Long adminId, Integer status);

    // ========== 角色管理 ==========

    /** 查询所有角色 */
    List<SysRoleDTO> listRoles();

    /** 新增角色 */
    SysRoleDTO addRole(SysRoleDTO roleDTO);

    /** 修改角色 */
    void updateRole(SysRoleDTO roleDTO);

    /** 删除角色 */
    void deleteRole(Long roleId);

    PageResult<SysNotificationDTO> listNotifications(int pageNum, int pageSize, String keyword, Integer messageType, Integer isPublished);

    SysNotificationDTO getNotificationById(Long id);

    SysNotificationDTO addNotification(SysNotificationDTO notificationDTO);

    void updateNotification(SysNotificationDTO notificationDTO);

    void deleteNotification(Long id);

    void publishNotification(Long id);

    /** 为管理员分配角色 */
    void assignRoles(Long adminId, List<Long> roleIds);

    // ========== 操作日志 / 安全审计 (Ultra) ==========

    /** 查询最近的操作日志 */
    PageResult<SysOperationLogDTO> listOperationLogs(int pageNum, int pageSize, Long userId, String module);

    /** 查询某用户最近的操作记录 */
    List<SysOperationLogDTO> listUserRecentOps(Long userId, int limit);

    /** 查询失败操作（可疑行为检测） */
    List<SysOperationLogDTO> listFailedOps(int hours, int limit);

    /** 统计今日操作数 */
    int countTodayOps();
}
