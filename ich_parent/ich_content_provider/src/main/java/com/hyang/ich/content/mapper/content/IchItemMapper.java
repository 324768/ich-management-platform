package com.hyang.ich.content.mapper.content;

import com.hyang.ich.content.entity.IchItem;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface IchItemMapper {

    List<IchItem> selectByCondition(@Param("categoryId") Long categoryId,
                                    @Param("keyword") String keyword,
                                    @Param("status") Integer status,
                                    @Param("offset") int offset,
                                    @Param("limit") int limit);

    int countByCondition(@Param("categoryId") Long categoryId,
                         @Param("keyword") String keyword,
                         @Param("status") Integer status);

    IchItem selectById(@Param("id") Long id);

    int insert(IchItem item);

    int update(IchItem item);

    int deleteById(@Param("id") Long id);

    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    long countAll();
}
