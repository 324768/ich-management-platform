package com.hyang.ich.content.mapper.content;

import com.hyang.ich.content.entity.IchActivityComment;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface IchActivityCommentMapper {

    List<IchActivityComment> selectByActivityId(@Param("activityId") Long activityId,
                                                 @Param("offset") int offset,
                                                 @Param("limit") int limit);

    int countByActivityId(@Param("activityId") Long activityId);

    int insert(IchActivityComment comment);

    int deleteById(@Param("id") Long id);

    int updateStatus(@Param("id") Long id, @Param("status") Integer status);
}
