package com.hyang.ich.omnitrix.mapper;

import com.hyang.ich.omnitrix.entity.AiUserBehavior;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface AiUserBehaviorMapper {

    int insert(AiUserBehavior behavior);

    List<Map<String, Object>> selectUserInterests(@Param("userId") Long userId, @Param("days") int days,
                                                   @Param("limit") int limit);
}
