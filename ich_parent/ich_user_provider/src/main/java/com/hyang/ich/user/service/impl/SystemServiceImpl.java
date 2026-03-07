package com.hyang.ich.user.service.impl;

import com.hyang.ich.common.exception.BusinessException;
import com.hyang.ich.common.utils.JwtUtils;
import com.hyang.ich.common.vo.PageResult;
import com.hyang.ich.system.SystemService;
import com.hyang.ich.system.dto.SysLoginDTO;
import com.hyang.ich.system.dto.SysRoleDTO;
import com.hyang.ich.system.dto.SysUserDTO;
import com.hyang.ich.user.entity.SysRole;
import com.hyang.ich.user.entity.SysUser;
import com.hyang.ich.user.entity.SysUserRole;
import com.hyang.ich.user.mapper.system.SysRoleMapper;
import com.hyang.ich.user.mapper.system.SysUserMapper;
import com.hyang.ich.user.mapper.system.SysUserRoleMapper;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;
import java.util.stream.Collectors;

@DubboService
public class SystemServiceImpl implements SystemService {

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private SysRoleMapper sysRoleMapper;

    @Autowired
    private SysUserRoleMapper sysUserRoleMapper;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    // ========== 管理员登录 ==========

    @Override
    public String login(SysLoginDTO loginDTO) {
        SysUser user = sysUserMapper.selectByUsername(loginDTO.getUsername());
        if (user == null) {
            throw new BusinessException(500, "管理员账号不存在");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException(500, "账号已被禁用");
        }
        if (!passwordEncoder.matches(loginDTO.getPassword(), user.getPassword())) {
            throw new BusinessException(500, "密码错误");
        }
        sysUserMapper.updateLoginInfo(user.getId(), null);
        return JwtUtils.generateToken(user.getId(), user.getUsername());
    }

    @Override
    public SysUserDTO getAdminInfo(Long adminId) {
        SysUser user = sysUserMapper.selectById(adminId);
        if (user == null) {
            throw new BusinessException(500, "管理员不存在");
        }
        SysUserDTO dto = toSysUserDTO(user);
        List<SysRole> roles = sysUserRoleMapper.selectRolesByUserId(adminId);
        dto.setRoles(roles.stream().map(this::toSysRoleDTO).collect(Collectors.toList()));
        return dto;
    }

    // ========== 管理员用户管理 ==========

    @Override
    public PageResult<SysUserDTO> listAdmins(int pageNum, int pageSize, String keyword) {
        int offset = (pageNum - 1) * pageSize;
        List<SysUser> users = sysUserMapper.selectByCondition(keyword, offset, pageSize);
        int total = sysUserMapper.countByCondition(keyword);
        List<SysUserDTO> dtoList = users.stream().map(this::toSysUserDTO).collect(Collectors.toList());
        return new PageResult<>(pageNum, pageSize, (long) total, dtoList);
    }

    @Override
    public SysUserDTO addAdmin(SysUserDTO sysUserDTO, String password) {
        SysUser existing = sysUserMapper.selectByUsername(sysUserDTO.getUsername());
        if (existing != null) {
            throw new BusinessException(500, "用户名已存在");
        }
        SysUser user = new SysUser();
        BeanUtils.copyProperties(sysUserDTO, user);
        user.setPassword(passwordEncoder.encode(password));
        if (user.getStatus() == null) user.setStatus(1);
        sysUserMapper.insert(user);
        sysUserDTO.setId(user.getId());
        return sysUserDTO;
    }

    @Override
    public void updateAdmin(SysUserDTO sysUserDTO) {
        SysUser user = new SysUser();
        BeanUtils.copyProperties(sysUserDTO, user);
        sysUserMapper.update(user);
    }

    @Override
    public void deleteAdmin(Long adminId) {
        sysUserMapper.deleteById(adminId);
    }

    @Override
    public void updateAdminStatus(Long adminId, Integer status) {
        sysUserMapper.updateStatus(adminId, status);
    }

    // ========== 角色管理 ==========

    @Override
    public List<SysRoleDTO> listRoles() {
        return sysRoleMapper.selectAll().stream().map(this::toSysRoleDTO).collect(Collectors.toList());
    }

    @Override
    public SysRoleDTO addRole(SysRoleDTO roleDTO) {
        SysRole role = new SysRole();
        BeanUtils.copyProperties(roleDTO, role);
        if (role.getStatus() == null) role.setStatus(1);
        if (role.getSort() == null) role.setSort(0);
        sysRoleMapper.insert(role);
        roleDTO.setId(role.getId());
        return roleDTO;
    }

    @Override
    public void updateRole(SysRoleDTO roleDTO) {
        SysRole role = new SysRole();
        BeanUtils.copyProperties(roleDTO, role);
        sysRoleMapper.update(role);
    }

    @Override
    public void deleteRole(Long roleId) {
        sysRoleMapper.deleteById(roleId);
    }

    @Override
    public void assignRoles(Long adminId, List<Long> roleIds) {
        sysUserRoleMapper.deleteByUserId(adminId);
        if (roleIds != null) {
            for (Long roleId : roleIds) {
                SysUserRole ur = new SysUserRole();
                ur.setUserId(adminId);
                ur.setRoleId(roleId);
                sysUserRoleMapper.insert(ur);
            }
        }
    }

    // ========== 转换方法 ==========

    private SysUserDTO toSysUserDTO(SysUser user) {
        SysUserDTO dto = new SysUserDTO();
        BeanUtils.copyProperties(user, dto);
        return dto;
    }

    private SysRoleDTO toSysRoleDTO(SysRole role) {
        SysRoleDTO dto = new SysRoleDTO();
        BeanUtils.copyProperties(role, dto);
        return dto;
    }
}
