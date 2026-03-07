package com.hyang.ich.content.mapper.product;

import com.hyang.ich.content.entity.Cart;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface CartMapper {

    List<Cart> selectByUserId(@Param("userId") Long userId);

    Cart selectByUserAndProduct(@Param("userId") Long userId, @Param("productId") Long productId);

    int insert(Cart cart);

    int updateQuantity(@Param("userId") Long userId, @Param("productId") Long productId, @Param("quantity") Integer quantity);

    int deleteByUserAndProduct(@Param("userId") Long userId, @Param("productId") Long productId);

    int deleteByUserId(@Param("userId") Long userId);

    int updateSelected(@Param("userId") Long userId, @Param("productId") Long productId, @Param("selected") Integer selected);

    int updateAllSelected(@Param("userId") Long userId, @Param("selected") Integer selected);
}
