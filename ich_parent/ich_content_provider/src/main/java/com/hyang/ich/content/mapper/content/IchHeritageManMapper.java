package com.hyang.ich.content.mapper.content;

import com.hyang.ich.content.entity.IchHeritageMan;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface IchHeritageManMapper {

    List<IchHeritageMan> selectByCondition(@Param("keyword") String keyword,
                                           @Param("status") Integer status,
                                           @Param("offset") int offset,
                                           @Param("limit") int limit);

    int countByCondition(@Param("keyword") String keyword,
                         @Param("status") Integer status);

    IchHeritageMan selectById(@Param("id") Long id);

    int insert(IchHeritageMan heritageMan);

    int update(IchHeritageMan heritageMan);

    int deleteById(@Param("id") Long id);
}
