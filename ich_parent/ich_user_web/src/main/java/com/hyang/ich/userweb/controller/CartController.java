package com.hyang.ich.userweb.controller;

import com.hyang.ich.common.vo.Result;
import com.hyang.ich.product.ProductService;
import com.hyang.ich.product.dto.CartDTO;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    @DubboReference(check = false)
    private ProductService productService;

    @GetMapping("/list")
    public Result<List<CartDTO>> listCart(@RequestParam Long userId) {
        return Result.success(productService.listCartItems(userId));
    }

    @PostMapping("/add")
    public Result<CartDTO> addToCart(@RequestParam Long userId,
                                     @RequestParam Long productId,
                                     @RequestParam(defaultValue = "1") Integer quantity) {
        return Result.success(productService.addToCart(userId, productId, quantity));
    }

    @PutMapping("/quantity")
    public Result<Void> updateQuantity(@RequestParam Long userId,
                                        @RequestParam Long productId,
                                        @RequestParam Integer quantity) {
        productService.updateCartQuantity(userId, productId, quantity);
        return Result.success();
    }

    @DeleteMapping("/remove")
    public Result<Void> removeFromCart(@RequestParam Long userId, @RequestParam Long productId) {
        productService.removeFromCart(userId, productId);
        return Result.success();
    }

    @DeleteMapping("/clear")
    public Result<Void> clearCart(@RequestParam Long userId) {
        productService.clearCart(userId);
        return Result.success();
    }

    @PutMapping("/check")
    public Result<Void> checkItem(@RequestParam Long userId,
                                   @RequestParam Long productId,
                                   @RequestParam Integer checked) {
        productService.checkCartItem(userId, productId, checked);
        return Result.success();
    }

    @PutMapping("/checkAll")
    public Result<Void> checkAll(@RequestParam Long userId, @RequestParam Integer checked) {
        productService.checkAllCartItems(userId, checked);
        return Result.success();
    }
}
