package com.hyang.ich.omnitrix.mapper;

import com.hyang.ich.omnitrix.entity.AiKnowledgeBase;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AiKnowledgeBaseMapper {

    List<AiKnowledgeBase> searchByKeyword(@Param("keyword") String keyword);

    List<AiKnowledgeBase> searchByTokens(@Param("tokens") List<String> tokens, @Param("limit") int limit);

    List<AiKnowledgeBase> selectAll();

    List<AiKnowledgeBase> selectPage(@Param("offset") int offset, @Param("limit") int limit,
                                      @Param("keyword") String keyword);

    int countAll(@Param("keyword") String keyword);

    int insert(AiKnowledgeBase kb);

    int update(AiKnowledgeBase kb);

    int deleteById(@Param("id") Long id);

    int incrementHitCount(@Param("id") Long id);

    List<AiKnowledgeBase> searchByKeywordAndCategory(@Param("keyword") String keyword,
                                                      @Param("categoryId") Long categoryId);
}
