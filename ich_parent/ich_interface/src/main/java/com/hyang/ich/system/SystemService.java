package com.hyang.ich.system;

import com.hyang.ich.common.vo.PageResult;
import com.hyang.ich.system.dto.SysLoginDTO;
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

    /** 为管理员分配角色 */
    void assignRoles(Long adminId, List<Long> roleIds);
}
