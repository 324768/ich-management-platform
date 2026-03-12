package com.hyang.ich.product;

import com.hyang.ich.common.vo.PageResult;
import com.hyang.ich.product.dto.CartDTO;
import com.hyang.ich.product.dto.InventoryRecordDTO;
import com.hyang.ich.product.dto.ProductCategoryDTO;
import com.hyang.ich.product.dto.ProductDTO;

import java.math.BigDecimal;
import java.util.List;

public interface ProductService {

    // ========== 商品分类 ==========

    List<ProductCategoryDTO> listCategoryTree();

    List<ProductCategoryDTO> listCategories(Long parentId);

    ProductCategoryDTO getCategoryById(Long id);

    ProductCategoryDTO addCategory(ProductCategoryDTO categoryDTO);

    void updateCategory(ProductCategoryDTO categoryDTO);

    void deleteCategory(Long id);

    // ========== 商品 ==========

    PageResult<ProductDTO> listProducts(int pageNum, int pageSize, Long categoryId, String keyword, Integer status);

    /** 按价格范围查询商品 */
    PageResult<ProductDTO> listProductsByPriceRange(int pageNum, int pageSize, BigDecimal minPrice, BigDecimal maxPrice, String keyword, Integer status);

    ProductDTO getProductById(Long id);

    ProductDTO addProduct(ProductDTO productDTO);

    void updateProduct(ProductDTO productDTO);

    void deleteProduct(Long id);

    void updateProductStatus(Long id, Integer status);

    /** 扣减库存 */
    void reduceStock(Long productId, Integer quantity);

    PageResult<InventoryRecordDTO> listInventoryRecords(int pageNum, int pageSize, String keyword, Integer type);

    InventoryRecordDTO addInventoryRecord(InventoryRecordDTO inventoryRecordDTO);

    void deleteInventoryRecord(Long id);

    // ========== 购物车 ==========

    List<CartDTO> listCartItems(Long userId);

    CartDTO addToCart(Long userId, Long productId, Integer quantity);

    void updateCartQuantity(Long userId, Long productId, Integer quantity);

    void removeFromCart(Long userId, Long productId);

    void clearCart(Long userId);

    void checkCartItem(Long userId, Long productId, Integer checked);

    void checkAllCartItems(Long userId, Integer checked);

    /** 统计商品总数 */
    long countProducts();

    /** 统计低库存商品数（库存低于阈值） */
    long countLowStockProducts(int threshold);

    /** 查询低库存商品列表 */
    List<ProductDTO> listLowStockProducts(int threshold);
}
