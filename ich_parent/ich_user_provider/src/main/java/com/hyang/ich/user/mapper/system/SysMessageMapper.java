package com.hyang.ich.user.mapper.system;

import com.hyang.ich.user.entity.SysMessage;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface SysMessageMapper {

    List<SysMessage> selectByCondition(@Param("keyword") String keyword,
                                       @Param("messageType") Integer messageType,
                                       @Param("isPublished") Integer isPublished,
                                       @Param("offset") int offset,
                                       @Param("limit") int limit);

    int countByCondition(@Param("keyword") String keyword,
                         @Param("messageType") Integer messageType,
                         @Param("isPublished") Integer isPublished);

    SysMessage selectById(@Param("id") Long id);

    int insert(SysMessage sysMessage);

    int update(SysMessage sysMessage);

    int deleteById(@Param("id") Long id);

    int publish(@Param("id") Long id);
}
