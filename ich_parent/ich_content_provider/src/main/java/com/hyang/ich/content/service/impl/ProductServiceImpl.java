package com.hyang.ich.content.service.impl;

import com.hyang.ich.common.vo.PageResult;
import com.hyang.ich.content.entity.Cart;
import com.hyang.ich.content.entity.IchCategory;
import com.hyang.ich.content.entity.IchHeritageMan;
import com.hyang.ich.content.entity.InventoryRecord;
import com.hyang.ich.content.entity.Product;
import com.hyang.ich.content.entity.ProductCategory;
import com.hyang.ich.content.mapper.content.IchCategoryMapper;
import com.hyang.ich.content.mapper.content.IchHeritageManMapper;
import com.hyang.ich.content.mapper.product.CartMapper;
import com.hyang.ich.content.mapper.product.InventoryRecordMapper;
import com.hyang.ich.content.mapper.product.ProductCategoryMapper;
import com.hyang.ich.content.mapper.product.ProductMapper;
import com.hyang.ich.product.ProductService;
import com.hyang.ich.product.dto.CartDTO;
import com.hyang.ich.product.dto.InventoryRecordDTO;
import com.hyang.ich.product.dto.ProductCategoryDTO;
import com.hyang.ich.product.dto.ProductDTO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@DubboService
public class ProductServiceImpl implements ProductService {

    @Autowired
    private ProductCategoryMapper categoryMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private InventoryRecordMapper inventoryRecordMapper;

    @Autowired
    private CartMapper cartMapper;

    @Autowired
    private IchCategoryMapper ichCategoryMapper;

    @Autowired
    private IchHeritageManMapper heritageManMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ========== 商品分类 ==========

    @Override
    public List<ProductCategoryDTO> listCategoryTree() {
        List<ProductCategory> all = categoryMapper.selectAll();
        List<ProductCategoryDTO> dtoList = all.stream().map(this::toCategoryDTO).collect(Collectors.toList());
        Map<Long, List<ProductCategoryDTO>> grouped = dtoList.stream()
                .filter(c -> c.getParentId() != null && c.getParentId() != 0)
                .collect(Collectors.groupingBy(ProductCategoryDTO::getParentId));
        List<ProductCategoryDTO> tree = new ArrayList<>();
        for (ProductCategoryDTO dto : dtoList) {
            if (dto.getParentId() == null || dto.getParentId() == 0) {
                dto.setChildren(grouped.getOrDefault(dto.getId(), new ArrayList<>()));
                tree.add(dto);
            }
        }
        return tree;
    }

    @Override
    public List<ProductCategoryDTO> listCategories(Long parentId) {
        return categoryMapper.selectByParentId(parentId).stream()
                .map(this::toCategoryDTO).collect(Collectors.toList());
    }

    @Override
    public ProductCategoryDTO getCategoryById(Long id) {
        ProductCategory c = categoryMapper.selectById(id);
        if (c == null) return null;
        ProductCategoryDTO dto = toCategoryDTO(c);
        if (c.getIchCategoryId() != null) {
            IchCategory ichCat = ichCategoryMapper.selectById(c.getIchCategoryId());
            if (ichCat != null) dto.setIchCategoryName(ichCat.getName());
        }
        return dto;
    }

    @Override
    public ProductCategoryDTO addCategory(ProductCategoryDTO dto) {
        ProductCategory entity = new ProductCategory();
        BeanUtils.copyProperties(dto, entity, "detailImages", "videos");
        entity.setDetailImages(toJsonString(dto.getDetailImages()));
        entity.setVideos(toJsonString(dto.getVideos()));
        if (entity.getStatus() == null) entity.setStatus(1);
        if (entity.getSort() == null) entity.setSort(0);
        if (entity.getParentId() == null) entity.setParentId(0L);
        entity.setLevel(entity.getParentId() == 0 ? 1 : 2);
        categoryMapper.insert(entity);
        dto.setId(entity.getId());
        return dto;
    }

    @Override
    public void updateCategory(ProductCategoryDTO dto) {
        ProductCategory entity = new ProductCategory();
        BeanUtils.copyProperties(dto, entity, "detailImages", "videos");
        entity.setDetailImages(toJsonString(dto.getDetailImages()));
        entity.setVideos(toJsonString(dto.getVideos()));
        categoryMapper.update(entity);
    }

    @Override
    public void deleteCategory(Long id) {
        categoryMapper.deleteById(id);
    }

    // ========== 商品 ==========

    @Override
    public PageResult<ProductDTO> listProducts(int pageNum, int pageSize, Long categoryId, String keyword, Integer status) {
        int offset = (pageNum - 1) * pageSize;
        List<Product> products = productMapper.selectByCondition(categoryId, keyword, status, offset, pageSize);
        int total = productMapper.countByCondition(categoryId, keyword, status);
        List<ProductDTO> dtoList = products.stream().map(this::toProductDTO).collect(Collectors.toList());
        return new PageResult<>(pageNum, pageSize, (long) total, dtoList);
    }

    @Override
    public PageResult<ProductDTO> listProductsByPriceRange(int pageNum, int pageSize, BigDecimal minPrice, BigDecimal maxPrice, String keyword, Integer status) {
        int offset = (pageNum - 1) * pageSize;
        List<Product> products = productMapper.selectByPriceRange(minPrice, maxPrice, keyword, status, offset, pageSize);
        int total = productMapper.countByPriceRange(minPrice, maxPrice, keyword, status);
        List<ProductDTO> dtoList = products.stream().map(this::toProductDTO).collect(Collectors.toList());
        return new PageResult<>(pageNum, pageSize, (long) total, dtoList);
    }

    @Override
    public ProductDTO getProductById(Long id) {
        Product p = productMapper.selectById(id);
        if (p == null) return null;
        ProductDTO dto = toProductDTO(p);
        if (p.getCategoryId() != null) {
            ProductCategory cat = categoryMapper.selectById(p.getCategoryId());
            if (cat != null) dto.setCategoryName(cat.getName());
        }
        if (p.getHeritageManId() != null) {
            IchHeritageMan hm = heritageManMapper.selectById(p.getHeritageManId());
            if (hm != null) dto.setHeritageManName(hm.getName());
        }
        return dto;
    }

    @Override
    public ProductDTO addProduct(ProductDTO dto) {
        Product entity = new Product();
        BeanUtils.copyProperties(dto, entity, "videos");
        entity.setVideos(toJsonString(dto.getVideos()));
        if (entity.getStatus() == null) entity.setStatus(1);
        if (entity.getStock() == null) entity.setStock(0);
        productMapper.insert(entity);
        dto.setId(entity.getId());
        return dto;
    }

    @Override
    public void updateProduct(ProductDTO dto) {
        Product entity = new Product();
        BeanUtils.copyProperties(dto, entity, "videos");
        entity.setVideos(toJsonString(dto.getVideos()));
        productMapper.update(entity);
    }

    @Override
    public void deleteProduct(Long id) {
        productMapper.deleteById(id);
    }

    @Override
    public void updateProductStatus(Long id, Integer status) {
        productMapper.updateStatus(id, status);
    }

    @Override
    public void reduceStock(Long productId, Integer quantity) {
        int rows = productMapper.reduceStock(productId, quantity);
        if (rows == 0) {
            throw new RuntimeException("库存不足");
        }
    }

    @Override
    public PageResult<InventoryRecordDTO> listInventoryRecords(int pageNum, int pageSize, String keyword, Integer type) {
        int offset = (pageNum - 1) * pageSize;
        List<InventoryRecord> list = inventoryRecordMapper.selectByCondition(keyword, type, offset, pageSize);
        int total = inventoryRecordMapper.countByCondition(keyword, type);
        List<InventoryRecordDTO> dtoList = list.stream().map(this::toInventoryRecordDTO).collect(Collectors.toList());
        return new PageResult<>(pageNum, pageSize, (long) total, dtoList);
    }

    @Override
    public InventoryRecordDTO addInventoryRecord(InventoryRecordDTO dto) {
        Product product = productMapper.selectById(dto.getProductId());
        if (product == null) {
            throw new RuntimeException("商品不存在");
        }
        int beforeStock = product.getStock() == null ? 0 : product.getStock();
        int inputQuantity = dto.getQuantity() == null ? 0 : dto.getQuantity();
        int recordQuantity = inputQuantity;
        int afterStock;
        if (dto.getType() != null && dto.getType() == 2) {
            if (beforeStock < inputQuantity) {
                throw new RuntimeException("库存不足");
            }
            recordQuantity = -inputQuantity;
            afterStock = beforeStock - inputQuantity;
        } else if (dto.getType() != null && dto.getType() == 3) {
            afterStock = inputQuantity;
            recordQuantity = inputQuantity - beforeStock;
        } else {
            afterStock = beforeStock + inputQuantity;
        }

        Product updateProduct = new Product();
        updateProduct.setId(product.getId());
        updateProduct.setStock(afterStock);
        productMapper.update(updateProduct);

        InventoryRecord entity = new InventoryRecord();
        entity.setProductId(product.getId());
        entity.setProductName(product.getName());
        entity.setType(dto.getType());
        entity.setQuantity(recordQuantity);
        entity.setBeforeStock(beforeStock);
        entity.setAfterStock(afterStock);
        entity.setReason(dto.getReason());
        entity.setOperatorId(dto.getOperatorId());
        entity.setOperatorName(dto.getOperatorName());
        inventoryRecordMapper.insert(entity);

        dto.setId(entity.getId());
        dto.setProductName(product.getName());
        dto.setQuantity(recordQuantity);
        dto.setBeforeStock(beforeStock);
        dto.setAfterStock(afterStock);
        return dto;
    }

    @Override
    public void deleteInventoryRecord(Long id) {
        inventoryRecordMapper.deleteById(id);
    }

    // ========== 购物车 ==========

    @Override
    public List<CartDTO> listCartItems(Long userId) {
        List<Cart> carts = cartMapper.selectByUserId(userId);
        return carts.stream().map(c -> {
            CartDTO dto = toCartDTO(c);
            Product p = productMapper.selectById(c.getProductId());
            if (p != null) {
                dto.setProductName(p.getName());
                dto.setProductImage(p.getMainImage());
                dto.setProductPrice(p.getPrice());
                dto.setProductStock(p.getStock());
            }
            return dto;
        }).collect(Collectors.toList());
    }

    @Override
    public CartDTO addToCart(Long userId, Long productId, Integer quantity) {
        Cart existing = cartMapper.selectByUserAndProduct(userId, productId);
        if (existing != null) {
            cartMapper.updateQuantity(userId, productId, existing.getQuantity() + quantity);
            existing.setQuantity(existing.getQuantity() + quantity);
            return toCartDTO(existing);
        }
        Cart cart = new Cart();
        cart.setUserId(userId);
        cart.setProductId(productId);
        cart.setQuantity(quantity);
        cart.setSelected(1);
        cartMapper.insert(cart);
        return toCartDTO(cart);
    }

    @Override
    public void updateCartQuantity(Long userId, Long productId, Integer quantity) {
        cartMapper.updateQuantity(userId, productId, quantity);
    }

    @Override
    public void removeFromCart(Long userId, Long productId) {
        cartMapper.deleteByUserAndProduct(userId, productId);
    }

    @Override
    public void clearCart(Long userId) {
        cartMapper.deleteByUserId(userId);
    }

    @Override
    public void checkCartItem(Long userId, Long productId, Integer checked) {
        cartMapper.updateSelected(userId, productId, checked);
    }

    @Override
    public void checkAllCartItems(Long userId, Integer checked) {
        cartMapper.updateAllSelected(userId, checked);
    }

    @Override
    public long countProducts() {
        return productMapper.countAll();
    }

    @Override
    public long countLowStockProducts(int threshold) {
        return productMapper.countLowStock(threshold);
    }

    @Override
    public List<ProductDTO> listLowStockProducts(int threshold) {
        return productMapper.selectLowStock(threshold).stream().map(this::toProductDTO).collect(Collectors.toList());
    }

    // ========== 转换方法 ==========

    private ProductCategoryDTO toCategoryDTO(ProductCategory entity) {
        ProductCategoryDTO dto = new ProductCategoryDTO();
        BeanUtils.copyProperties(entity, dto, "detailImages", "videos");
        dto.setDetailImages(fromJsonString(entity.getDetailImages()));
        dto.setVideos(fromJsonString(entity.getVideos()));
        return dto;
    }

    private String toJsonString(List<String> list) {
        if (list == null || list.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private List<String> fromJsonString(String json) {
        if (json == null || json.isEmpty()) return new ArrayList<>();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            return new ArrayList<>();
        }
    }

    private ProductDTO toProductDTO(Product entity) {
        ProductDTO dto = new ProductDTO();
        BeanUtils.copyProperties(entity, dto, "videos");
        dto.setVideos(fromJsonString(entity.getVideos()));
        return dto;
    }

    private InventoryRecordDTO toInventoryRecordDTO(InventoryRecord entity) {
        InventoryRecordDTO dto = new InventoryRecordDTO();
        BeanUtils.copyProperties(entity, dto);
        return dto;
    }

    private CartDTO toCartDTO(Cart entity) {
        CartDTO dto = new CartDTO();
        BeanUtils.copyProperties(entity, dto);
        return dto;
    }
}
