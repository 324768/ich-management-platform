package com.hyang.ich.adminweb.controller;

import com.hyang.ich.common.vo.PageResult;
import com.hyang.ich.common.vo.Result;
import com.hyang.ich.product.ProductService;
import com.hyang.ich.product.dto.ProductCategoryDTO;
import com.hyang.ich.product.dto.ProductDTO;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/product")
public class AdminProductController {

    @DubboReference(check = false)
    private ProductService productService;

    // ========== 商品分类 ==========

    @GetMapping("/category/tree")
    public Result<List<ProductCategoryDTO>> listCategoryTree() {
        return Result.success(productService.listCategoryTree());
    }

    @PostMapping("/category/add")
    public Result<ProductCategoryDTO> addCategory(@RequestBody ProductCategoryDTO dto) {
        return Result.success(productService.addCategory(dto));
    }

    @PutMapping("/category/update")
    public Result<Void> updateCategory(@RequestBody ProductCategoryDTO dto) {
        productService.updateCategory(dto);
        return Result.success();
    }

    @GetMapping("/category/{id}")
    public Result<ProductCategoryDTO> getCategory(@PathVariable Long id) {
        return Result.success(productService.getCategoryById(id));
    }

    @DeleteMapping("/category/{id}")
    public Result<Void> deleteCategory(@PathVariable Long id) {
        productService.deleteCategory(id);
        return Result.success();
    }

    // ========== 商品管理 ==========

    @GetMapping("/list")
    public Result<PageResult<ProductDTO>> listProducts(@RequestParam(defaultValue = "1") int pageNum,
                                                        @RequestParam(defaultValue = "10") int pageSize,
                                                        @RequestParam(required = false) Long categoryId,
                                                        @RequestParam(required = false) String keyword,
                                                        @RequestParam(required = false) Integer status) {
        return Result.success(productService.listProducts(pageNum, pageSize, categoryId, keyword, status));
    }

    @GetMapping("/{id}")
    public Result<ProductDTO> getProduct(@PathVariable Long id) {
        return Result.success(productService.getProductById(id));
    }

    @PostMapping("/add")
    public Result<ProductDTO> addProduct(@RequestBody ProductDTO dto) {
        return Result.success(productService.addProduct(dto));
    }

    @PutMapping("/update")
    public Result<Void> updateProduct(@RequestBody ProductDTO dto) {
        productService.updateProduct(dto);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return Result.success();
    }

    @PutMapping("/status")
    public Result<Void> updateProductStatus(@RequestParam Long id, @RequestParam Integer status) {
        productService.updateProductStatus(id, status);
        return Result.success();
    }

    @GetMapping("/low-stock")
    public Result<List<ProductDTO>> listLowStock(@RequestParam(defaultValue = "10") int threshold) {
        return Result.success(productService.listLowStockProducts(threshold));
    }
}
