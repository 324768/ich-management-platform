package com.hyang.ich.content.mapper.content;

import com.hyang.ich.content.entity.IchCategory;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface IchCategoryMapper {

    List<IchCategory> selectByParentId(@Param("parentId") Long parentId);

    List<IchCategory> selectAll();

    IchCategory selectById(@Param("id") Long id);

    int insert(IchCategory category);

    int update(IchCategory category);

    int deleteById(@Param("id") Long id);
}
