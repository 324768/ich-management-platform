package com.hyang.ich.user.mapper.system;

import com.hyang.ich.user.entity.SysOperationLog;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface SysOperationLogMapper {

    List<SysOperationLog> selectByCondition(@Param("userId") Long userId,
                                             @Param("module") String module,
                                             @Param("offset") int offset,
                                             @Param("limit") int limit);

    int countByCondition(@Param("userId") Long userId, @Param("module") String module);

    List<SysOperationLog> selectUserRecentOps(@Param("userId") Long userId, @Param("limit") int limit);

    List<SysOperationLog> selectFailedOps(@Param("hours") int hours, @Param("limit") int limit);

    int countTodayOps();
}
