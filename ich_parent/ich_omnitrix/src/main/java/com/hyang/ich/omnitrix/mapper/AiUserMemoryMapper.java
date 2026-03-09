package com.hyang.ich.omnitrix.mapper;

import com.hyang.ich.omnitrix.entity.AiUserMemory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AiUserMemoryMapper {

    int insert(AiUserMemory memory);

    int updateValue(@Param("id") Long id, @Param("memoryValue") String memoryValue,
                    @Param("confidence") java.math.BigDecimal confidence);

    int incrementHitCount(@Param("id") Long id);

    int deactivate(@Param("id") Long id);

    List<AiUserMemory> selectByUserId(@Param("userId") Long userId);

    List<AiUserMemory> selectByUserIdAndType(@Param("userId") Long userId, @Param("memoryType") String memoryType);

    AiUserMemory selectByUserIdAndKey(@Param("userId") Long userId, @Param("memoryKey") String memoryKey);

    int countByUserId(@Param("userId") Long userId);
}
