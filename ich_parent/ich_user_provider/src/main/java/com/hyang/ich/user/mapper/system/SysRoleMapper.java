package com.hyang.ich.user.mapper.system;

import com.hyang.ich.user.entity.SysRole;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface SysRoleMapper {

    List<SysRole> selectAll();

    SysRole selectById(@Param("id") Long id);

    int insert(SysRole role);

    int update(SysRole role);

    int deleteById(@Param("id") Long id);
}
