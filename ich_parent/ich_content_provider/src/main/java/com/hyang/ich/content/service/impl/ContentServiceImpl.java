package com.hyang.ich.content.service.impl;

import com.hyang.ich.common.vo.PageResult;
import com.hyang.ich.content.ContentService;
import com.hyang.ich.content.dto.IchActivityCommentDTO;
import com.hyang.ich.content.dto.IchActivityDTO;
import com.hyang.ich.content.dto.IchActivityRecordDTO;
import com.hyang.ich.content.dto.IchActivityViewLogDTO;
import com.hyang.ich.content.dto.IchCategoryDTO;
import com.hyang.ich.content.dto.IchHeritageManDTO;
import com.hyang.ich.content.dto.IchItemDTO;
import com.hyang.ich.content.dto.IchPostDTO;
import com.hyang.ich.content.dto.IchPostCommentDTO;
import com.hyang.ich.content.entity.IchActivity;
import com.hyang.ich.content.entity.IchActivityComment;
import com.hyang.ich.content.entity.IchActivityRecord;
import com.hyang.ich.content.entity.IchActivityViewLog;
import com.hyang.ich.content.entity.IchCategory;
import com.hyang.ich.content.entity.IchHeritageMan;
import com.hyang.ich.content.entity.IchItem;
import com.hyang.ich.content.entity.IchPost;
import com.hyang.ich.content.entity.IchPostComment;
import com.hyang.ich.content.mapper.content.IchActivityCommentMapper;
import com.hyang.ich.content.mapper.content.IchActivityMapper;
import com.hyang.ich.content.mapper.content.IchActivityRecordMapper;
import com.hyang.ich.content.mapper.content.IchActivityViewLogMapper;
import com.hyang.ich.content.mapper.content.IchCategoryMapper;
import com.hyang.ich.content.mapper.content.IchHeritageManMapper;
import com.hyang.ich.content.mapper.content.IchItemMapper;
import com.hyang.ich.content.mapper.content.IchPostMapper;
import com.hyang.ich.content.mapper.content.IchPostCommentMapper;
import com.hyang.ich.content.mapper.content.IchPostLikeMapper;
import com.hyang.ich.content.mapper.content.IchPostFavoriteMapper;
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

    @Autowired
    private IchActivityMapper activityMapper;

    @Autowired
    private IchActivityRecordMapper activityRecordMapper;

    @Autowired
    private IchActivityCommentMapper activityCommentMapper;

    @Autowired
    private IchActivityViewLogMapper activityViewLogMapper;

    @Autowired
    private IchPostMapper postMapper;

    @Autowired
    private IchPostCommentMapper postCommentMapper;

    @Autowired
    private IchPostLikeMapper postLikeMapper;

    @Autowired
    private IchPostFavoriteMapper postFavoriteMapper;

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
    public void updateCategory(IchCategoryDTO dto) {
        IchCategory entity = new IchCategory();
        BeanUtils.copyProperties(dto, entity, "detailImages", "videos");
        entity.setDetailImages(toJsonString(dto.getDetailImages()));
        entity.setVideos(toJsonString(dto.getVideos()));
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
        if (item.getCategoryId() != null) {
            IchCategory category = categoryMapper.selectById(item.getCategoryId());
            if (category != null) dto.setCategoryName(category.getName());
        }
        return dto;
    }

    @Override
    public IchItemDTO addItem(IchItemDTO dto) {
        IchItem entity = new IchItem();
        BeanUtils.copyProperties(dto, entity, "detailImages", "videos");
        entity.setDetailImages(toJsonString(dto.getDetailImages()));
        entity.setVideos(toJsonString(dto.getVideos()));
        if (entity.getStatus() == null) entity.setStatus(0);
        itemMapper.insert(entity);
        dto.setId(entity.getId());
        return dto;
    }

    @Override
    public void updateItem(IchItemDTO dto) {
        IchItem entity = new IchItem();
        BeanUtils.copyProperties(dto, entity, "detailImages", "videos");
        entity.setDetailImages(toJsonString(dto.getDetailImages()));
        entity.setVideos(toJsonString(dto.getVideos()));
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
        if (man == null) return null;
        IchHeritageManDTO dto = toHeritageManDTO(man);
        if (man.getCategoryId() != null) {
            IchCategory cat = categoryMapper.selectById(man.getCategoryId());
            if (cat != null) dto.setCategoryName(cat.getName());
        }
        if (man.getItemId() != null) {
            IchItem item = itemMapper.selectById(man.getItemId());
            if (item != null) dto.setItemName(item.getName());
        }
        return dto;
    }

    @Override
    public IchHeritageManDTO addHeritageMan(IchHeritageManDTO dto) {
        IchHeritageMan entity = new IchHeritageMan();
        BeanUtils.copyProperties(dto, entity, "detailImages", "videos");
        entity.setDetailImages(toJsonString(dto.getDetailImages()));
        entity.setVideos(toJsonString(dto.getVideos()));
        if (entity.getStatus() == null) entity.setStatus(1);
        heritageManMapper.insert(entity);
        dto.setId(entity.getId());
        return dto;
    }

    @Override
    public void updateHeritageMan(IchHeritageManDTO dto) {
        IchHeritageMan entity = new IchHeritageMan();
        BeanUtils.copyProperties(dto, entity, "detailImages", "videos");
        entity.setDetailImages(toJsonString(dto.getDetailImages()));
        entity.setVideos(toJsonString(dto.getVideos()));
        heritageManMapper.update(entity);
    }

    @Override
    public void deleteHeritageMan(Long id) {
        heritageManMapper.deleteById(id);
    }

    @Override
    public PageResult<IchActivityDTO> listActivities(int pageNum, int pageSize, String keyword, Integer status, Integer activityType) {
        int offset = (pageNum - 1) * pageSize;
        List<IchActivity> list = activityMapper.selectByCondition(keyword, status, activityType, null, offset, pageSize);
        int total = activityMapper.countByCondition(keyword, status, activityType, null);
        List<IchActivityDTO> dtoList = list.stream().map(this::toActivityDTO).collect(Collectors.toList());
        return new PageResult<>(pageNum, pageSize, (long) total, dtoList);
    }

    @Override
    public IchActivityDTO getActivityById(Long id) {
        IchActivity activity = activityMapper.selectById(id);
        return activity != null ? toActivityDTO(activity) : null;
    }

    @Override
    public IchActivityDTO addActivity(IchActivityDTO dto) {
        IchActivity entity = new IchActivity();
        BeanUtils.copyProperties(dto, entity);
        if (entity.getStatus() == null) entity.setStatus(0);
        if (entity.getSort() == null) entity.setSort(0);
        if (entity.getCurrentParticipants() == null) entity.setCurrentParticipants(0);
        // ≥100人的大型活动需管理员审批，<100人直接发布(无需审批)
        if (entity.getMaxParticipants() != null && entity.getMaxParticipants() >= 100) {
            entity.setApprovalStatus(1); // 待审批
            entity.setStatus(0);         // 草稿状态，等审批通过后自动改为报名中
        } else {
            entity.setApprovalStatus(0); // 无需审批
        }
        activityMapper.insert(entity);
        dto.setId(entity.getId());
        dto.setApprovalStatus(entity.getApprovalStatus());
        if (dto.getCurrentParticipants() == null) dto.setCurrentParticipants(0);
        return dto;
    }

    @Override
    public void updateActivity(IchActivityDTO dto) {
        IchActivity entity = new IchActivity();
        BeanUtils.copyProperties(dto, entity);
        activityMapper.update(entity);
    }

    @Override
    public void deleteActivity(Long id) {
        activityMapper.deleteById(id);
    }

    @Override
    public void updateActivityStatus(Long id, Integer status) {
        activityMapper.updateStatus(id, status);
    }

    @Override
    public PageResult<IchActivityRecordDTO> listActivityRecords(int pageNum, int pageSize, String keyword, Integer status, Long activityId) {
        int offset = (pageNum - 1) * pageSize;
        List<IchActivityRecord> list = activityRecordMapper.selectByCondition(keyword, status, activityId, offset, pageSize);
        int total = activityRecordMapper.countByCondition(keyword, status, activityId);
        List<IchActivityRecordDTO> dtoList = list.stream().map(this::toActivityRecordDTO).collect(Collectors.toList());
        return new PageResult<>(pageNum, pageSize, (long) total, dtoList);
    }

    @Override
    public void updateActivityRecordStatus(Long id, Integer status) {
        activityRecordMapper.updateStatus(id, status);
    }

    @Override
    public void deleteActivityRecord(Long id) {
        activityRecordMapper.deleteById(id);
    }

    @Override
    public IchActivityRecordDTO registerActivity(IchActivityRecordDTO recordDTO) {
        // 原子性 +1，若已满则 affected = 0
        int affected = activityMapper.incrementParticipants(recordDTO.getActivityId());
        if (affected == 0) {
            return null; // 已满或活动不存在
        }
        IchActivityRecord entity = new IchActivityRecord();
        BeanUtils.copyProperties(recordDTO, entity);
        if (entity.getStatus() == null) entity.setStatus(0);
        activityRecordMapper.insert(entity);
        recordDTO.setId(entity.getId());
        return recordDTO;
    }

    // ========== 活动审批 ==========

    @Override
    public PageResult<IchActivityDTO> listActivitiesByApproval(int pageNum, int pageSize, String keyword, Integer approvalStatus) {
        int offset = (pageNum - 1) * pageSize;
        List<IchActivity> list = activityMapper.selectByApprovalStatus(approvalStatus, keyword, offset, pageSize);
        int total = activityMapper.countByApprovalStatus(approvalStatus, keyword);
        List<IchActivityDTO> dtoList = list.stream().map(this::toActivityDTO).collect(Collectors.toList());
        return new PageResult<>(pageNum, pageSize, (long) total, dtoList);
    }

    @Override
    public void updateActivityApprovalStatus(Long id, Integer approvalStatus, String rejectReason, Long reviewerId) {
        activityMapper.updateApprovalStatus(id, approvalStatus, rejectReason, reviewerId);
        // 审批通过时，自动将活动状态设为"报名中"(1)
        if (approvalStatus != null && approvalStatus == 2) {
            activityMapper.updateStatus(id, 1);
        }
    }

    // ========== 活动评论 ==========

    @Override
    public PageResult<IchActivityCommentDTO> listActivityComments(Long activityId, int pageNum, int pageSize) {
        int offset = (pageNum - 1) * pageSize;
        List<IchActivityComment> list = activityCommentMapper.selectByActivityId(activityId, offset, pageSize);
        int total = activityCommentMapper.countByActivityId(activityId);
        List<IchActivityCommentDTO> dtoList = list.stream().map(this::toCommentDTO).collect(Collectors.toList());
        return new PageResult<>(pageNum, pageSize, (long) total, dtoList);
    }

    @Override
    public IchActivityCommentDTO addActivityComment(IchActivityCommentDTO commentDTO) {
        IchActivityComment entity = new IchActivityComment();
        BeanUtils.copyProperties(commentDTO, entity);
        if (entity.getStatus() == null) entity.setStatus(0);
        activityCommentMapper.insert(entity);
        commentDTO.setId(entity.getId());
        return commentDTO;
    }

    @Override
    public void deleteActivityComment(Long id) {
        activityCommentMapper.deleteById(id);
    }

    @Override
    public void updateActivityCommentStatus(Long id, Integer status) {
        activityCommentMapper.updateStatus(id, status);
    }

    // ========== 活动浏览记录 ==========

    @Override
    public PageResult<IchActivityViewLogDTO> listActivityViewLogs(Long activityId, int pageNum, int pageSize) {
        int offset = (pageNum - 1) * pageSize;
        List<IchActivityViewLog> list = activityViewLogMapper.selectByActivityId(activityId, offset, pageSize);
        int total = activityViewLogMapper.countByActivityId(activityId);
        List<IchActivityViewLogDTO> dtoList = list.stream().map(this::toViewLogDTO).collect(Collectors.toList());
        return new PageResult<>(pageNum, pageSize, (long) total, dtoList);
    }

    @Override
    public void addActivityViewLog(IchActivityViewLogDTO viewLogDTO) {
        IchActivityViewLog entity = new IchActivityViewLog();
        BeanUtils.copyProperties(viewLogDTO, entity);
        activityViewLogMapper.insert(entity);
    }

    @Override
    public long countItems() {
        return itemMapper.countAll();
    }

    // ========== 转换方法 ==========

    private IchCategoryDTO toCategoryDTO(IchCategory entity) {
        IchCategoryDTO dto = new IchCategoryDTO();
        BeanUtils.copyProperties(entity, dto, "detailImages", "videos");
        dto.setDetailImages(parseJsonList(entity.getDetailImages()));
        dto.setVideos(parseJsonList(entity.getVideos()));
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
        BeanUtils.copyProperties(entity, dto, "detailImages", "videos");
        dto.setDetailImages(parseJsonList(entity.getDetailImages()));
        dto.setVideos(parseJsonList(entity.getVideos()));
        return dto;
    }

    private IchHeritageManDTO toHeritageManDTO(IchHeritageMan entity) {
        IchHeritageManDTO dto = new IchHeritageManDTO();
        BeanUtils.copyProperties(entity, dto, "detailImages", "videos");
        dto.setDetailImages(parseJsonList(entity.getDetailImages()));
        dto.setVideos(parseJsonList(entity.getVideos()));
        return dto;
    }

    private IchActivityDTO toActivityDTO(IchActivity entity) {
        IchActivityDTO dto = new IchActivityDTO();
        BeanUtils.copyProperties(entity, dto);
        return dto;
    }

    private IchActivityRecordDTO toActivityRecordDTO(IchActivityRecord entity) {
        IchActivityRecordDTO dto = new IchActivityRecordDTO();
        BeanUtils.copyProperties(entity, dto);
        return dto;
    }

    private IchActivityCommentDTO toCommentDTO(IchActivityComment entity) {
        IchActivityCommentDTO dto = new IchActivityCommentDTO();
        BeanUtils.copyProperties(entity, dto);
        return dto;
    }

    private IchActivityViewLogDTO toViewLogDTO(IchActivityViewLog entity) {
        IchActivityViewLogDTO dto = new IchActivityViewLogDTO();
        BeanUtils.copyProperties(entity, dto);
        return dto;
    }

    // ========== 非遗动态/笔记 ==========

    @Override
    public PageResult<IchPostDTO> listPosts(int pageNum, int pageSize, String keyword, Integer type) {
        int offset = (pageNum - 1) * pageSize;
        List<IchPost> list = postMapper.selectList(keyword, type, offset, pageSize);
        int total = postMapper.count(keyword, type);
        List<IchPostDTO> dtoList = list.stream().map(this::toPostDTO).collect(Collectors.toList());
        return new PageResult<>(pageNum, pageSize, total, dtoList);
    }

    @Override
    public IchPostDTO getPostById(Long id) {
        IchPost entity = postMapper.selectById(id);
        if (entity == null) return null;
        postMapper.incrementViewCount(id);
        return toPostDTO(entity);
    }

    @Override
    public IchPostDTO addPost(IchPostDTO postDTO) {
        IchPost entity = new IchPost();
        BeanUtils.copyProperties(postDTO, entity);
        if (entity.getStatus() == null) entity.setStatus(1);
        if (entity.getType() == null) entity.setType(1);
        postMapper.insert(entity);
        return toPostDTO(entity);
    }

    @Override
    public void updatePost(IchPostDTO postDTO) {
        IchPost entity = new IchPost();
        BeanUtils.copyProperties(postDTO, entity);
        postMapper.update(entity);
    }

    @Override
    public void deletePost(Long id) {
        postMapper.deleteById(id);
    }

    @Override
    public void likePost(Long postId, Long userId) {
        int inserted = postLikeMapper.insert(postId, userId);
        if (inserted > 0) {
            postMapper.incrementLikeCount(postId);
        }
    }

    @Override
    public void unlikePost(Long postId, Long userId) {
        int deleted = postLikeMapper.delete(postId, userId);
        if (deleted > 0) {
            postMapper.decrementLikeCount(postId);
        }
    }

    @Override
    public void favoritePost(Long postId, Long userId) {
        int inserted = postFavoriteMapper.insert(postId, userId);
        if (inserted > 0) {
            postMapper.incrementFavoriteCount(postId);
        }
    }

    @Override
    public void unfavoritePost(Long postId, Long userId) {
        int deleted = postFavoriteMapper.delete(postId, userId);
        if (deleted > 0) {
            postMapper.decrementFavoriteCount(postId);
        }
    }

    @Override
    public boolean hasLikedPost(Long postId, Long userId) {
        return postLikeMapper.exists(postId, userId) > 0;
    }

    @Override
    public boolean hasFavoritedPost(Long postId, Long userId) {
        return postFavoriteMapper.exists(postId, userId) > 0;
    }

    @Override
    public PageResult<IchPostDTO> listUserPosts(Long userId, int pageNum, int pageSize) {
        int offset = (pageNum - 1) * pageSize;
        List<IchPost> list = postMapper.selectByUserId(userId, offset, pageSize);
        int total = postMapper.countByUserId(userId);
        List<IchPostDTO> dtoList = list.stream().map(this::toPostDTO).collect(Collectors.toList());
        return new PageResult<>(pageNum, pageSize, total, dtoList);
    }

    @Override
    public PageResult<IchPostDTO> listUserLikedPosts(Long userId, int pageNum, int pageSize) {
        int offset = (pageNum - 1) * pageSize;
        List<IchPost> list = postLikeMapper.selectPostsByUserId(userId, offset, pageSize);
        int total = postLikeMapper.countByUserId(userId);
        List<IchPostDTO> dtoList = list.stream().map(this::toPostDTO).collect(Collectors.toList());
        return new PageResult<>(pageNum, pageSize, total, dtoList);
    }

    @Override
    public PageResult<IchPostDTO> listUserFavoritedPosts(Long userId, int pageNum, int pageSize) {
        int offset = (pageNum - 1) * pageSize;
        List<IchPost> list = postFavoriteMapper.selectPostsByUserId(userId, offset, pageSize);
        int total = postFavoriteMapper.countByUserId(userId);
        List<IchPostDTO> dtoList = list.stream().map(this::toPostDTO).collect(Collectors.toList());
        return new PageResult<>(pageNum, pageSize, total, dtoList);
    }

    @Override
    public PageResult<IchPostCommentDTO> listPostComments(Long postId, int pageNum, int pageSize) {
        int offset = (pageNum - 1) * pageSize;
        List<IchPostComment> list = postCommentMapper.selectByPostId(postId, offset, pageSize);
        int total = postCommentMapper.countByPostId(postId);
        List<IchPostCommentDTO> dtoList = list.stream().map(this::toPostCommentDTO).collect(Collectors.toList());
        return new PageResult<>(pageNum, pageSize, total, dtoList);
    }

    @Override
    public IchPostCommentDTO addPostComment(IchPostCommentDTO commentDTO) {
        IchPostComment entity = new IchPostComment();
        BeanUtils.copyProperties(commentDTO, entity);
        if (entity.getStatus() == null) entity.setStatus(1);
        postCommentMapper.insert(entity);
        postMapper.incrementCommentCount(commentDTO.getPostId());
        return toPostCommentDTO(entity);
    }

    @Override
    public void deletePostComment(Long id) {
        postCommentMapper.deleteById(id);
    }

    private IchPostDTO toPostDTO(IchPost entity) {
        IchPostDTO dto = new IchPostDTO();
        BeanUtils.copyProperties(entity, dto);
        return dto;
    }

    private IchPostCommentDTO toPostCommentDTO(IchPostComment entity) {
        IchPostCommentDTO dto = new IchPostCommentDTO();
        BeanUtils.copyProperties(entity, dto);
        return dto;
    }
}
