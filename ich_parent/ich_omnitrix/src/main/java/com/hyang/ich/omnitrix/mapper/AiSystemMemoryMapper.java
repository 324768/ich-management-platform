package com.hyang.ich.omnitrix.mapper;

import com.hyang.ich.omnitrix.entity.AiSystemMemory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AiSystemMemoryMapper {

    int insert(AiSystemMemory memory);

    int updateValue(@Param("id") Long id, @Param("memoryValue") String memoryValue,
                    @Param("confidence") java.math.BigDecimal confidence);

    int incrementHitCount(@Param("id") Long id);

    int deactivate(@Param("id") Long id);

    List<AiSystemMemory> selectAll();

    List<AiSystemMemory> selectByType(@Param("memoryType") String memoryType);

    List<AiSystemMemory> selectActive();

    AiSystemMemory selectByKey(@Param("memoryKey") String memoryKey);

    int countActive();
}
