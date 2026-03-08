package com.hyang.ich.content.mapper.content;

import com.hyang.ich.content.entity.IchActivityRecord;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface IchActivityRecordMapper {

    List<IchActivityRecord> selectByCondition(@Param("keyword") String keyword,
                                              @Param("status") Integer status,
                                              @Param("activityId") Long activityId,
                                              @Param("offset") int offset,
                                              @Param("limit") int limit);

    int countByCondition(@Param("keyword") String keyword,
                         @Param("status") Integer status,
                         @Param("activityId") Long activityId);

    int insert(IchActivityRecord record);

    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    int deleteById(@Param("id") Long id);
}
