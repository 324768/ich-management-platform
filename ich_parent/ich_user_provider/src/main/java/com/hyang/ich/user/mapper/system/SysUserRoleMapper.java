package com.hyang.ich.user.mapper.system;

import com.hyang.ich.user.entity.SysRole;
import com.hyang.ich.user.entity.SysUserRole;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface SysUserRoleMapper {

    List<SysRole> selectRolesByUserId(@Param("userId") Long userId);

    int insert(SysUserRole userRole);

    int deleteByUserId(@Param("userId") Long userId);
}
