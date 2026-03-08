package com.hyang.ich.content.mapper.content;

import com.hyang.ich.content.entity.IchActivityViewLog;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface IchActivityViewLogMapper {

    List<IchActivityViewLog> selectByActivityId(@Param("activityId") Long activityId,
                                                 @Param("offset") int offset,
                                                 @Param("limit") int limit);

    int countByActivityId(@Param("activityId") Long activityId);

    int insert(IchActivityViewLog viewLog);
}
