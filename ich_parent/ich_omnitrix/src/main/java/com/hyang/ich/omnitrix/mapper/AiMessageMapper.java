package com.hyang.ich.omnitrix.mapper;

import com.hyang.ich.omnitrix.entity.AiMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AiMessageMapper {

    int insert(AiMessage message);

    List<AiMessage> selectByConversationId(@Param("conversationId") Long conversationId);

    List<AiMessage> selectBySessionId(@Param("sessionId") String sessionId);

    int countByConversationId(@Param("conversationId") Long conversationId);

    AiMessage selectLastBySessionIdAndRole(@Param("sessionId") String sessionId, @Param("role") String role);

    int deleteById(@Param("id") Long id);
}
