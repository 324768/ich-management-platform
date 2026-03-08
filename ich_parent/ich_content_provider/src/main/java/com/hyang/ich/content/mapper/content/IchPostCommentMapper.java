package com.hyang.ich.content.mapper.content;

import com.hyang.ich.content.entity.IchPostComment;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface IchPostCommentMapper {

    List<IchPostComment> selectByPostId(@Param("postId") Long postId,
                                         @Param("offset") int offset, @Param("limit") int limit);

    int countByPostId(@Param("postId") Long postId);

    int insert(IchPostComment comment);

    int deleteById(@Param("id") Long id);
}
