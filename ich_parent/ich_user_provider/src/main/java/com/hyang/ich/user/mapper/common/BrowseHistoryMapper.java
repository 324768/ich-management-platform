package com.hyang.ich.user.mapper.common;

import com.hyang.ich.user.entity.BrowseHistory;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface BrowseHistoryMapper {

    int insert(BrowseHistory record);

    List<BrowseHistory> selectByDate(@Param("userId") Long userId, @Param("date") String date);

    List<BrowseHistory> selectRecent(@Param("userId") Long userId, @Param("days") int days);

    List<BrowseHistory> selectByType(@Param("userId") Long userId, @Param("targetType") String targetType, @Param("limit") int limit);

    int countByDate(@Param("userId") Long userId, @Param("date") String date);

    List<String> selectBrowseDates(@Param("userId") Long userId, @Param("days") int days);
}
