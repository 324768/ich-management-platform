package com.hyang.ich.omnitrix.mapper;

import com.hyang.ich.omnitrix.entity.AiTraceSpan;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface AiTraceSpanMapper {

    int insert(AiTraceSpan span);

    List<AiTraceSpan> selectByTraceId(@Param("traceId") String traceId);

    /** 按 Agent 聚合统计：平均延迟、成功率、调用次数 */
    List<Map<String, Object>> agentPerformanceStats(@Param("days") int days);

    /** 按 Span 类型聚合统计 */
    List<Map<String, Object>> spanTypeStats(@Param("days") int days);
}
