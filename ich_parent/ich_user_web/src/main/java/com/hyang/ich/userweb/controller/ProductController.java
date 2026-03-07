package com.hyang.ich.userweb.controller;

import com.hyang.ich.common.vo.PageResult;
import com.hyang.ich.common.vo.Result;
import com.hyang.ich.product.ProductService;
import com.hyang.ich.product.dto.ProductCategoryDTO;
import com.hyang.ich.product.dto.ProductDTO;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/product")
public class ProductController {

    @DubboReference(check = false)
    private ProductService productService;

    @GetMapping("/category/tree")
    public Result<List<ProductCategoryDTO>> listCategoryTree() {
        return Result.success(productService.listCategoryTree());
    }

    @GetMapping("/list")
    public Result<PageResult<ProductDTO>> listProducts(@RequestParam(defaultValue = "1") int pageNum,
                                                        @RequestParam(defaultValue = "10") int pageSize,
                                                        @RequestParam(required = false) Long categoryId,
                                                        @RequestParam(required = false) String keyword) {
        return Result.success(productService.listProducts(pageNum, pageSize, categoryId, keyword, 1));
    }

    @GetMapping("/{id}")
    public Result<ProductDTO> getProduct(@PathVariable Long id) {
        return Result.success(productService.getProductById(id));
    }
}
