package com.hyang.ich.user.mapper.system;

import com.hyang.ich.user.entity.SysUser;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface SysUserMapper {

    SysUser selectById(@Param("id") Long id);

    SysUser selectByUsername(@Param("username") String username);

    List<SysUser> selectByCondition(@Param("keyword") String keyword,
                                    @Param("offset") int offset, @Param("limit") int limit);

    int countByCondition(@Param("keyword") String keyword);

    int insert(SysUser sysUser);

    int update(SysUser sysUser);

    int deleteById(@Param("id") Long id);

    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    int updateLoginInfo(@Param("id") Long id, @Param("lastLoginIp") String lastLoginIp);
}
