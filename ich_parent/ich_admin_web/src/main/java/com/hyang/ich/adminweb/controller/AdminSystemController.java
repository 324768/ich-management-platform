package com.hyang.ich.adminweb.controller;

import com.hyang.ich.common.vo.PageResult;
import com.hyang.ich.common.vo.Result;
import com.hyang.ich.system.SystemService;
import com.hyang.ich.system.dto.SysRoleDTO;
import com.hyang.ich.system.dto.SysUserDTO;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/system")
public class AdminSystemController {

    @DubboReference(check = false)
    private SystemService systemService;

    // ========== 管理员管理 ==========

    @GetMapping("/admin/list")
    public Result<PageResult<SysUserDTO>> listAdmins(@RequestParam(defaultValue = "1") int pageNum,
                                                      @RequestParam(defaultValue = "10") int pageSize,
                                                      @RequestParam(required = false) String keyword) {
        return Result.success(systemService.listAdmins(pageNum, pageSize, keyword));
    }

    @GetMapping("/admin/{id}")
    public Result<SysUserDTO> getAdmin(@PathVariable Long id) {
        return Result.success(systemService.getAdminInfo(id));
    }

    @PostMapping("/admin/add")
    public Result<SysUserDTO> addAdmin(@RequestBody SysUserDTO sysUserDTO, @RequestParam String password) {
        return Result.success(systemService.addAdmin(sysUserDTO, password));
    }

    @PutMapping("/admin/update")
    public Result<Void> updateAdmin(@RequestBody SysUserDTO sysUserDTO) {
        systemService.updateAdmin(sysUserDTO);
        return Result.success();
    }

    @DeleteMapping("/admin/{id}")
    public Result<Void> deleteAdmin(@PathVariable Long id) {
        systemService.deleteAdmin(id);
        return Result.success();
    }

    @PutMapping("/admin/status")
    public Result<Void> updateAdminStatus(@RequestParam Long adminId, @RequestParam Integer status) {
        systemService.updateAdminStatus(adminId, status);
        return Result.success();
    }

    @PutMapping("/admin/roles")
    public Result<Void> assignRoles(@RequestParam Long adminId, @RequestBody List<Long> roleIds) {
        systemService.assignRoles(adminId, roleIds);
        return Result.success();
    }

    // ========== 角色管理 ==========

    @GetMapping("/role/list")
    public Result<List<SysRoleDTO>> listRoles() {
        return Result.success(systemService.listRoles());
    }

    @PostMapping("/role/add")
    public Result<SysRoleDTO> addRole(@RequestBody SysRoleDTO roleDTO) {
        return Result.success(systemService.addRole(roleDTO));
    }

    @PutMapping("/role/update")
    public Result<Void> updateRole(@RequestBody SysRoleDTO roleDTO) {
        systemService.updateRole(roleDTO);
        return Result.success();
    }

    @DeleteMapping("/role/{id}")
    public Result<Void> deleteRole(@PathVariable Long id) {
        systemService.deleteRole(id);
        return Result.success();
    }
}
