package com.hyang.ich.content.mapper.content;

import com.hyang.ich.content.entity.VideoExhibition;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface VideoExhibitionMapper {
    List<VideoExhibition> selectAll();
    List<VideoExhibition> selectByStatus(@Param("status") Integer status);
    VideoExhibition selectById(@Param("id") Long id);
    void insert(VideoExhibition entity);
    void update(VideoExhibition entity);
    void deleteById(@Param("id") Long id);
    long countAll();
}
