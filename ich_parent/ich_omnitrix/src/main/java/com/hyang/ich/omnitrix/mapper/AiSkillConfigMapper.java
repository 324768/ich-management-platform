package com.hyang.ich.omnitrix.mapper;

import com.hyang.ich.omnitrix.entity.AiSkillConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * AI技能配置Mapper
 */
@Mapper
public interface AiSkillConfigMapper {

    /**
     * 查询所有启用的Skills
     */
    List<AiSkillConfig> selectEnabled();

    /**
     * 查询所有Skills
     */
    List<AiSkillConfig> selectAll();

    /**
     * 根据ID查询
     */
    AiSkillConfig selectById(@Param("skillId") String skillId);

    /**
     * 根据ID查询版本号
     */
    Integer selectVersionById(@Param("skillId") String skillId);

    /**
     * 分页查询
     */
    List<AiSkillConfig> selectPage(@Param("offset") int offset, @Param("limit") int limit,
                                    @Param("keyword") String keyword);

    /**
     * 统计总数
     */
    int countAll(@Param("keyword") String keyword);

    /**
     * 插入
     */
    int insert(AiSkillConfig config);

    /**
     * 更新
     */
    int update(AiSkillConfig config);

    /**
     * 删除
     */
    int deleteById(@Param("skillId") String skillId);

    /**
     * 更新启用状态
     */
    int updateEnabled(@Param("skillId") String skillId, @Param("enabled") Integer enabled);

    /**
     * 批量更新启用状态
     */
    int batchUpdateEnabled(@Param("skillIds") List<String> skillIds, @Param("enabled") Integer enabled);
}
