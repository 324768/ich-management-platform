package com.hyang.ich.adminweb.controller;

import com.hyang.ich.common.vo.PageResult;
import com.hyang.ich.common.vo.Result;
import com.hyang.ich.product.ProductService;
import com.hyang.ich.product.dto.InventoryRecordDTO;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/inventory/record")
public class AdminInventoryController {

    @DubboReference(check = false)
    private ProductService productService;

    @GetMapping("/list")
    public Result<PageResult<InventoryRecordDTO>> listInventoryRecords(@RequestParam(defaultValue = "1") int pageNum,
                                                                       @RequestParam(defaultValue = "10") int pageSize,
                                                                       @RequestParam(required = false) String keyword,
                                                                       @RequestParam(required = false) Integer type) {
        return Result.success(productService.listInventoryRecords(pageNum, pageSize, keyword, type));
    }

    @PostMapping("/add")
    public Result<InventoryRecordDTO> addInventoryRecord(@RequestBody InventoryRecordDTO dto) {
        return Result.success(productService.addInventoryRecord(dto));
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteInventoryRecord(@PathVariable Long id) {
        productService.deleteInventoryRecord(id);
        return Result.success();
    }
}
