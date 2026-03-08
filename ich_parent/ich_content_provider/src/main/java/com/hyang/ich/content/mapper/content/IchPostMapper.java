package com.hyang.ich.content.mapper.content;

import com.hyang.ich.content.entity.IchPost;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface IchPostMapper {

    List<IchPost> selectList(@Param("keyword") String keyword, @Param("type") Integer type,
                             @Param("offset") int offset, @Param("limit") int limit);

    int count(@Param("keyword") String keyword, @Param("type") Integer type);

    IchPost selectById(@Param("id") Long id);

    int insert(IchPost post);

    int update(IchPost post);

    int deleteById(@Param("id") Long id);

    int incrementViewCount(@Param("id") Long id);

    int incrementLikeCount(@Param("id") Long id);

    int incrementCommentCount(@Param("id") Long id);

    int decrementCommentCount(@Param("id") Long id);

    int decrementLikeCount(@Param("id") Long id);

    int incrementFavoriteCount(@Param("id") Long id);

    int decrementFavoriteCount(@Param("id") Long id);

    List<IchPost> selectByUserId(@Param("userId") Long userId, @Param("offset") int offset, @Param("limit") int limit);

    int countByUserId(@Param("userId") Long userId);
}
