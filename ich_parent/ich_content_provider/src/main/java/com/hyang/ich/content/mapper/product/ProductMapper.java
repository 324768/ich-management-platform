package com.hyang.ich.content.mapper.product;

import com.hyang.ich.content.entity.Product;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ProductMapper {

    List<Product> selectByCondition(@Param("categoryId") Long categoryId,
                                   @Param("keyword") String keyword,
                                   @Param("status") Integer status,
                                   @Param("offset") int offset,
                                   @Param("limit") int limit);

    int countByCondition(@Param("categoryId") Long categoryId,
                         @Param("keyword") String keyword,
                         @Param("status") Integer status);

    Product selectById(@Param("id") Long id);

    int insert(Product product);

    int update(Product product);

    int deleteById(@Param("id") Long id);

    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    int reduceStock(@Param("id") Long id, @Param("quantity") Integer quantity);

    long countAll();

    long countLowStock(@Param("threshold") int threshold);
}
