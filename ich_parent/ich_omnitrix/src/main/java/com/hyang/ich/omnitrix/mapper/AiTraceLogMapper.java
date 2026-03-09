package com.hyang.ich.omnitrix.mapper;

import com.hyang.ich.omnitrix.entity.AiTraceLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Mapper
public interface AiTraceLogMapper {

    int insert(AiTraceLog traceLog);

    int updateSelfScore(@Param("traceId") String traceId, @Param("selfScore") BigDecimal selfScore);

    int updateMultiDimensionScore(@Param("traceId") String traceId,
                                  @Param("selfScore") BigDecimal selfScore,
                                  @Param("accuracyScore") BigDecimal accuracyScore,
                                  @Param("completenessScore") BigDecimal completenessScore,
                                  @Param("safetyScore") BigDecimal safetyScore);

    List<AiTraceLog> selectPage(@Param("offset") int offset, @Param("limit") int limit,
                                 @Param("startDate") String startDate, @Param("endDate") String endDate);

    int countAll(@Param("startDate") String startDate, @Param("endDate") String endDate);

    /** Dashboard 统计 */
    int countTotalConversations();

    int countTotalMessages();

    int countTodayMessages();

    Double avgSelfScore();

    Double avgLatencyMs();

    List<Map<String, Object>> topAgents();

    List<Map<String, Object>> scoreDistribution();

    int updateUserFeedback(@Param("traceId") String traceId, @Param("userFeedback") int userFeedback);

    String selectTraceIdByMessageId(@Param("messageId") Long messageId);
}
