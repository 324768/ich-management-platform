package com.hyang.ich.content.mapper.product;

import com.hyang.ich.content.entity.InventoryRecord;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface InventoryRecordMapper {

    List<InventoryRecord> selectByCondition(@Param("keyword") String keyword,
                                            @Param("type") Integer type,
                                            @Param("offset") int offset,
                                            @Param("limit") int limit);

    int countByCondition(@Param("keyword") String keyword,
                         @Param("type") Integer type);

    int insert(InventoryRecord inventoryRecord);

    int deleteById(@Param("id") Long id);
}
