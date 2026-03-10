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

    /** 查询某 promptKey 的所有版本（按版本号降序） */
    List<AiPromptConfig> selectVersionsByKey(@Param("promptKey") String promptKey);

    /** 查询某 promptKey 的最大版本号 */
    Integer selectMaxVersion(@Param("promptKey") String promptKey);

    /** 将某 promptKey 的所有版本标记为非激活 */
    int deactivateAllVersions(@Param("promptKey") String promptKey);

    /** 激活指定 ID 的版本 */
    int activateVersion(@Param("id") Long id);
}
