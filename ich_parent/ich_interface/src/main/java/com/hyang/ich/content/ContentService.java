package com.hyang.ich.content;

import com.hyang.ich.common.vo.PageResult;
import com.hyang.ich.content.dto.IchActivityCommentDTO;
import com.hyang.ich.content.dto.IchActivityDTO;
import com.hyang.ich.content.dto.IchActivityRecordDTO;
import com.hyang.ich.content.dto.IchActivityViewLogDTO;
import com.hyang.ich.content.dto.IchCategoryDTO;
import com.hyang.ich.content.dto.IchHeritageManDTO;
import com.hyang.ich.content.dto.IchItemDTO;
import com.hyang.ich.content.dto.IchPostDTO;
import com.hyang.ich.content.dto.IchPostCommentDTO;

import java.util.List;

public interface ContentService {

    // ========== 非遗分类 ==========

    /** 查询所有分类（树形结构） */
    List<IchCategoryDTO> listCategoryTree();

    /** 查询分类列表 */
    List<IchCategoryDTO> listCategories(Long parentId);

    /** 根据ID查询分类 */
    IchCategoryDTO getCategoryById(Long id);

    /** 新增分类 */
    IchCategoryDTO addCategory(IchCategoryDTO categoryDTO);

    /** 修改分类 */
    void updateCategory(IchCategoryDTO categoryDTO);

    /** 删除分类 */
    void deleteCategory(Long id);

    // ========== 非遗项目 ==========

    /** 分页查询非遗项目 */
    PageResult<IchItemDTO> listItems(int pageNum, int pageSize, Long categoryId, String keyword, Integer status);

    /** 根据ID查询非遗项目 */
    IchItemDTO getItemById(Long id);

    /** 新增非遗项目 */
    IchItemDTO addItem(IchItemDTO itemDTO);

    /** 修改非遗项目 */
    void updateItem(IchItemDTO itemDTO);

    /** 删除非遗项目 */
    void deleteItem(Long id);

    /** 更新项目状态（上架/下架） */
    void updateItemStatus(Long id, Integer status);

    // ========== 传承人 ==========

    /** 分页查询传承人 */
    PageResult<IchHeritageManDTO> listHeritageMan(int pageNum, int pageSize, String keyword, Integer status);

    /** 根据ID查询传承人 */
    IchHeritageManDTO getHeritageManById(Long id);

    /** 新增传承人 */
    IchHeritageManDTO addHeritageMan(IchHeritageManDTO heritageManDTO);

    /** 修改传承人 */
    void updateHeritageMan(IchHeritageManDTO heritageManDTO);

    /** 删除传承人 */
    void deleteHeritageMan(Long id);

    PageResult<IchActivityDTO> listActivities(int pageNum, int pageSize, String keyword, Integer status, Integer activityType);

    IchActivityDTO getActivityById(Long id);

    IchActivityDTO addActivity(IchActivityDTO activityDTO);

    void updateActivity(IchActivityDTO activityDTO);

    void deleteActivity(Long id);

    void updateActivityStatus(Long id, Integer status);

    PageResult<IchActivityRecordDTO> listActivityRecords(int pageNum, int pageSize, String keyword, Integer status, Long activityId);

    void updateActivityRecordStatus(Long id, Integer status);

    void deleteActivityRecord(Long id);

    /**
     * 活动报名 — 检查人数上限，成功后 currentParticipants + 1
     * @return 报名记录 DTO，若已满则返回 null
     */
    IchActivityRecordDTO registerActivity(IchActivityRecordDTO recordDTO);

    // ========== 活动审批 ==========

    /** 按审批状态分页查询活动 */
    PageResult<IchActivityDTO> listActivitiesByApproval(int pageNum, int pageSize, String keyword, Integer approvalStatus);

    /** 更新活动审批状态 */
    void updateActivityApprovalStatus(Long id, Integer approvalStatus, String rejectReason, Long reviewerId);

    // ========== 活动评论 ==========

    PageResult<IchActivityCommentDTO> listActivityComments(Long activityId, int pageNum, int pageSize);

    IchActivityCommentDTO addActivityComment(IchActivityCommentDTO commentDTO);

    void deleteActivityComment(Long id);

    void updateActivityCommentStatus(Long id, Integer status);

    // ========== 活动浏览记录 ==========

    PageResult<IchActivityViewLogDTO> listActivityViewLogs(Long activityId, int pageNum, int pageSize);

    void addActivityViewLog(IchActivityViewLogDTO viewLogDTO);

    /** 统计非遗项目总数 */
    long countItems();

    // ========== 非遗动态/笔记 ==========

    PageResult<IchPostDTO> listPosts(int pageNum, int pageSize, String keyword, Integer type);

    IchPostDTO getPostById(Long id);

    IchPostDTO addPost(IchPostDTO postDTO);

    void updatePost(IchPostDTO postDTO);

    void deletePost(Long id);

    void likePost(Long postId, Long userId);

    void unlikePost(Long postId, Long userId);

    void favoritePost(Long postId, Long userId);

    void unfavoritePost(Long postId, Long userId);

    boolean hasLikedPost(Long postId, Long userId);

    boolean hasFavoritedPost(Long postId, Long userId);

    PageResult<IchPostDTO> listUserPosts(Long userId, int pageNum, int pageSize);

    PageResult<IchPostDTO> listUserLikedPosts(Long userId, int pageNum, int pageSize);

    PageResult<IchPostDTO> listUserFavoritedPosts(Long userId, int pageNum, int pageSize);

    // ========== 非遗动态评论 ==========

    PageResult<IchPostCommentDTO> listPostComments(Long postId, int pageNum, int pageSize);

    IchPostCommentDTO addPostComment(IchPostCommentDTO commentDTO);

    void deletePostComment(Long id);
}
