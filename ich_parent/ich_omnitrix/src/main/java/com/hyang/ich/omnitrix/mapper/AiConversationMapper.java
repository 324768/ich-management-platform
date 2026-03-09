package com.hyang.ich.omnitrix.mapper;

import com.hyang.ich.omnitrix.entity.AiConversation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AiConversationMapper {

    int insert(AiConversation conversation);

    AiConversation selectById(@Param("id") Long id);

    AiConversation selectBySessionId(@Param("sessionId") String sessionId);

    List<AiConversation> selectByUserId(@Param("userId") Long userId);

    int updateTitle(@Param("id") Long id, @Param("title") String title);

    int updateSummary(@Param("id") Long id, @Param("summary") String summary);

    int updateMessageCount(@Param("id") Long id, @Param("messageCount") Integer messageCount);

    int incrementMessageCount(@Param("id") Long id);

    int softDelete(@Param("id") Long id);

    List<AiConversation> selectAll(@Param("offset") int offset, @Param("limit") int limit);
}
