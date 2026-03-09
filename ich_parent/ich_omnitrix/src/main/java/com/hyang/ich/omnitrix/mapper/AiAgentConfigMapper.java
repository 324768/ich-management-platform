package com.hyang.ich.omnitrix.mapper;

import com.hyang.ich.omnitrix.entity.AiAgentConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AiAgentConfigMapper {

    List<AiAgentConfig> selectEnabled();

    AiAgentConfig selectByCode(@Param("agentCode") String agentCode);

    Long selectVersionByCode(@Param("agentCode") String agentCode);

    List<AiAgentConfig> selectPage(@Param("offset") int offset, @Param("limit") int limit,
                                    @Param("keyword") String keyword);

    int countAll(@Param("keyword") String keyword);

    int insert(AiAgentConfig config);

    int update(AiAgentConfig config);

    int deleteById(@Param("id") Long id);

    int updateStatus(@Param("id") Long id, @Param("status") Integer status);
}
