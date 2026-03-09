package com.hyang.ich.omnitrix.mapper;

import com.hyang.ich.omnitrix.entity.AiUserAiConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AiUserAiConfigMapper {

    int insert(AiUserAiConfig config);

    int update(AiUserAiConfig config);

    int updateAiEnabled(@Param("userId") Long userId, @Param("aiEnabled") Integer aiEnabled,
                        @Param("disabledReason") String disabledReason, @Param("disabledBy") Long disabledBy);

    AiUserAiConfig selectByUserId(@Param("userId") Long userId);

    List<AiUserAiConfig> selectDisabled();

    int deleteByUserId(@Param("userId") Long userId);
}
