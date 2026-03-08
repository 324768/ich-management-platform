package com.hyang.ich.content.mapper.content;

import com.hyang.ich.content.entity.IchPost;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface IchPostLikeMapper {

    int insert(@Param("postId") Long postId, @Param("userId") Long userId);

    int delete(@Param("postId") Long postId, @Param("userId") Long userId);

    int exists(@Param("postId") Long postId, @Param("userId") Long userId);

    List<IchPost> selectPostsByUserId(@Param("userId") Long userId, @Param("offset") int offset, @Param("limit") int limit);

    int countByUserId(@Param("userId") Long userId);
}
