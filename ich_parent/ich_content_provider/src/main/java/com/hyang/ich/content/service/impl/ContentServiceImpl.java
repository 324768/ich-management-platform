package com.hyang.ich.content.service.impl;

import com.hyang.ich.common.vo.PageResult;
import com.hyang.ich.content.ContentService;
import com.hyang.ich.content.dto.IchCategoryDTO;
import com.hyang.ich.content.dto.IchHeritageManDTO;
import com.hyang.ich.content.dto.IchItemDTO;
import com.hyang.ich.content.entity.IchCategory;
import com.hyang.ich.content.entity.IchHeritageMan;
import com.hyang.ich.content.entity.IchItem;
import com.hyang.ich.content.mapper.content.IchCategoryMapper;
import com.hyang.ich.content.mapper.content.IchHeritageManMapper;
import com.hyang.ich.content.mapper.content.IchItemMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@DubboService
public class ContentServiceImpl implements ContentService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private IchCategoryMapper categoryMapper;

    @Autowired
    private IchItemMapper itemMapper;

    @Autowired
    private IchHeritageManMapper heritageManMapper;

    // ========== 非遗分类 ==========

    @Override
    public List<IchCategoryDTO> listCategoryTree() {
        List<IchCategory> allCategories = categoryMapper.selectAll();
        List<IchCategoryDTO> dtoList = allCategories.stream().map(this::toCategoryDTO).collect(Collectors.toList());
        Map<Long, List<IchCategoryDTO>> grouped = dtoList.stream()
                .filter(c -> c.getParentId() != null && c.getParentId() != 0)
                .collect(Collectors.groupingBy(IchCategoryDTO::getParentId));
        List<IchCategoryDTO> tree = new ArrayList<>();
        for (IchCategoryDTO dto : dtoList) {
            if (dto.getParentId() == null || dto.getParentId() == 0) {
                dto.setChildren(grouped.getOrDefault(dto.getId(), new ArrayList<>()));
                tree.add(dto);
            }
        }
        return tree;
    }

    @Override
    public List<IchCategoryDTO> listCategories(Long parentId) {
        return categoryMapper.selectByParentId(parentId).stream()
                .map(this::toCategoryDTO).collect(Collectors.toList());
    }

    @Override
    public IchCategoryDTO getCategoryById(Long id) {
        IchCategory category = categoryMapper.selectById(id);
        return category != null ? toCategoryDTO(category) : null;
    }

    @Override
    public IchCategoryDTO addCategory(IchCategoryDTO dto) {
        IchCategory entity = new IchCategory();
        BeanUtils.copyProperties(dto, entity, "detailImages");
        entity.setDetailImages(toJsonString(dto.getDetailImages()));
        if (entity.getStatus() == null) entity.setStatus(1);
        if (entity.getSort() == null) entity.setSort(0);
        if (entity.getParentId() == null) entity.setParentId(0L);
        entity.setLevel(entity.getParentId() == 0 ? 1 : 2);
        categoryMapper.insert(entity);
        dto.setId(entity.getId());
        return dto;
    }

    @Override
    public void updateCategory(IchCategoryDTO dto) {
        IchCategory entity = new IchCategory();
        BeanUtils.copyProperties(dto, entity, "detailImages");
        entity.setDetailImages(toJsonString(dto.getDetailImages()));
        categoryMapper.update(entity);
    }

    @Override
    public void deleteCategory(Long id) {
        categoryMapper.deleteById(id);
    }

    // ========== 非遗项目 ==========

    @Override
    public PageResult<IchItemDTO> listItems(int pageNum, int pageSize, Long categoryId, String keyword, Integer status) {
        int offset = (pageNum - 1) * pageSize;
        List<IchItem> items = itemMapper.selectByCondition(categoryId, keyword, status, offset, pageSize);
        int total = itemMapper.countByCondition(categoryId, keyword, status);
        List<IchItemDTO> dtoList = items.stream().map(this::toItemDTO).collect(Collectors.toList());
        return new PageResult<>(pageNum, pageSize, (long) total, dtoList);
    }

    @Override
    public IchItemDTO getItemById(Long id) {
        IchItem item = itemMapper.selectById(id);
        if (item == null) return null;
        IchItemDTO dto = toItemDTO(item);
        IchCategory category = categoryMapper.selectById(item.getCategoryId());
        if (category != null) dto.setCategoryName(category.getName());
        return dto;
    }

    @Override
    public IchItemDTO addItem(IchItemDTO dto) {
        IchItem entity = new IchItem();
        BeanUtils.copyProperties(dto, entity);
        if (entity.getStatus() == null) entity.setStatus(0);
        itemMapper.insert(entity);
        dto.setId(entity.getId());
        return dto;
    }

    @Override
    public void updateItem(IchItemDTO dto) {
        IchItem entity = new IchItem();
        BeanUtils.copyProperties(dto, entity);
        itemMapper.update(entity);
    }

    @Override
    public void deleteItem(Long id) {
        itemMapper.deleteById(id);
    }

    @Override
    public void updateItemStatus(Long id, Integer status) {
        itemMapper.updateStatus(id, status);
    }

    // ========== 传承人 ==========

    @Override
    public PageResult<IchHeritageManDTO> listHeritageMan(int pageNum, int pageSize, String keyword, Integer status) {
        int offset = (pageNum - 1) * pageSize;
        List<IchHeritageMan> list = heritageManMapper.selectByCondition(keyword, status, offset, pageSize);
        int total = heritageManMapper.countByCondition(keyword, status);
        List<IchHeritageManDTO> dtoList = list.stream().map(this::toHeritageManDTO).collect(Collectors.toList());
        return new PageResult<>(pageNum, pageSize, (long) total, dtoList);
    }

    @Override
    public IchHeritageManDTO getHeritageManById(Long id) {
        IchHeritageMan man = heritageManMapper.selectById(id);
        return man != null ? toHeritageManDTO(man) : null;
    }

    @Override
    public IchHeritageManDTO addHeritageMan(IchHeritageManDTO dto) {
        IchHeritageMan entity = new IchHeritageMan();
        BeanUtils.copyProperties(dto, entity);
        if (entity.getStatus() == null) entity.setStatus(1);
        heritageManMapper.insert(entity);
        dto.setId(entity.getId());
        return dto;
    }

    @Override
    public void updateHeritageMan(IchHeritageManDTO dto) {
        IchHeritageMan entity = new IchHeritageMan();
        BeanUtils.copyProperties(dto, entity);
        heritageManMapper.update(entity);
    }

    @Override
    public void deleteHeritageMan(Long id) {
        heritageManMapper.deleteById(id);
    }

    @Override
    public long countItems() {
        return itemMapper.countAll();
    }

    // ========== 转换方法 ==========

    private IchCategoryDTO toCategoryDTO(IchCategory entity) {
        IchCategoryDTO dto = new IchCategoryDTO();
        BeanUtils.copyProperties(entity, dto, "detailImages");
        dto.setDetailImages(parseJsonList(entity.getDetailImages()));
        return dto;
    }

    private String toJsonString(List<String> list) {
        if (list == null || list.isEmpty()) return null;
        try { return objectMapper.writeValueAsString(list); } catch (Exception e) { return null; }
    }

    private List<String> parseJsonList(String json) {
        if (json == null || json.isEmpty()) return Collections.emptyList();
        try { return objectMapper.readValue(json, new TypeReference<List<String>>() {}); } catch (Exception e) { return Collections.emptyList(); }
    }

    private IchItemDTO toItemDTO(IchItem entity) {
        IchItemDTO dto = new IchItemDTO();
        BeanUtils.copyProperties(entity, dto);
        return dto;
    }

    private IchHeritageManDTO toHeritageManDTO(IchHeritageMan entity) {
        IchHeritageManDTO dto = new IchHeritageManDTO();
        BeanUtils.copyProperties(entity, dto);
        return dto;
    }
}
