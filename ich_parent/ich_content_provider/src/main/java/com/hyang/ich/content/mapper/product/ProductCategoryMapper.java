package com.hyang.ich.content.mapper.product;

import com.hyang.ich.content.entity.ProductCategory;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ProductCategoryMapper {

    List<ProductCategory> selectByParentId(@Param("parentId") Long parentId);

    List<ProductCategory> selectAll();

    ProductCategory selectById(@Param("id") Long id);

    int insert(ProductCategory category);

    int update(ProductCategory category);

    int deleteById(@Param("id") Long id);
}
