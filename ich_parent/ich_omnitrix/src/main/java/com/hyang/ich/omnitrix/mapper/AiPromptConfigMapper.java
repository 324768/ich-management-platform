package com.hyang.ich.omnitrix.mapper;

import com.hyang.ich.omnitrix.entity.AiPromptConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AiPromptConfigMapper {

    String selectContentByKey(@Param("promptKey") String promptKey);

    List<AiPromptConfig> selectAll();

    int insert(AiPromptConfig config);

    int update(AiPromptConfig config);

    int deleteById(@Param("id") Long id);
}
