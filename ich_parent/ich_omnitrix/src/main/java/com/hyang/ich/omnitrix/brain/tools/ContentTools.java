package com.hyang.ich.omnitrix.brain.tools;

import com.hyang.ich.common.vo.PageResult;
import com.hyang.ich.content.ContentService;
import com.hyang.ich.content.dto.IchActivityDTO;
import com.hyang.ich.content.dto.IchHeritageManDTO;
import com.hyang.ich.content.dto.IchItemDTO;
import com.hyang.ich.content.dto.IchPostDTO;
import com.hyang.ich.omnitrix.agent.AgentUtils;
import com.hyang.ich.omnitrix.brain.AiRequestContext;
import com.hyang.ich.omnitrix.dto.PendingAction;
import com.hyang.ich.omnitrix.orchestrator.ActionExecutor;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 非遗内容工具集 — 替代原 ContentSubAgent，提供 LangChain4j @Tool 方法。
 * <p>
 * 包含搜索非遗项目、传承人、活动，以及点赞/收藏/评论/报名等写操作。
 * 写操作通过 PendingAction 机制实现二次确认。
 */
@Slf4j
@Component
public class ContentTools {

    private final ContentService contentService;
    private final ActionExecutor actionExecutor;

    public ContentTools(ContentService contentService, ActionExecutor actionExecutor) {
        this.contentService = contentService;
        this.actionExecutor = actionExecutor;
    }

    @Tool("搜索非遗项目、非遗文化、传统技艺。参数: 搜索关键词")
    public String searchItems(String keyword) {
        if (AiRequestContext.isToolLoopDetected()) {
            return ToolResultWrapper.error("工具调用过于频繁，请直接回答用户").withErrorHint().toXml();
        }
        try {
            PageResult<IchItemDTO> items = contentService.listItems(1, 5, null, keyword, 1);
            if (items == null || items.getList() == null || items.getList().isEmpty()) {
                AiRequestContext.recordToolFailure();
                return ToolResultWrapper.empty("未找到与\"" + keyword + "\"相关的非遗项目")
                        .withSearchEmptyHint("非遗项目").toXml();
            }
            StringBuilder sb = new StringBuilder();
            for (IchItemDTO item : items.getList()) {
                sb.append("- ").append(item.getName());
                if (item.getDescription() != null) {
                    sb.append(" - ").append(AgentUtils.truncateDesc(item.getDescription(), 100));
                }
                sb.append("\n");
            }
            AiRequestContext.recordToolSuccess();
            return ToolResultWrapper.success("找到" + items.getList().size() + "个非遗项目", sb.toString())
                    .withSearchSuccessHint("非遗项目").toXml();
        } catch (Exception e) {
            log.debug("搜索非遗项目异常: {}", e.getMessage());
            AiRequestContext.recordToolFailure();
            return ToolResultWrapper.error("搜索非遗项目时出现异常").withErrorHint().toXml();
        }
    }

    @Tool("搜索传承人、非遗大师、手艺人。参数: 搜索关键词")
    public String searchHeritageMen(String keyword) {
        if (AiRequestContext.isToolLoopDetected()) {
            return ToolResultWrapper.error("工具调用过于频繁，请直接回答用户").withErrorHint().toXml();
        }
        try {
            PageResult<IchHeritageManDTO> men = contentService.listHeritageMan(1, 3, keyword, 1);
            if (men == null || men.getList() == null || men.getList().isEmpty()) {
                AiRequestContext.recordToolFailure();
                return ToolResultWrapper.empty("未找到与\"" + keyword + "\"相关的传承人")
                        .withSearchEmptyHint("传承人").toXml();
            }
            StringBuilder sb = new StringBuilder();
            for (IchHeritageManDTO man : men.getList()) {
                sb.append("- ").append(man.getName());
                if (man.getTitle() != null) sb.append(" (").append(man.getTitle()).append(")");
                sb.append("\n");
            }
            AiRequestContext.recordToolSuccess();
            return ToolResultWrapper.success("找到" + men.getList().size() + "位传承人", sb.toString())
                    .withSearchSuccessHint("传承人").toXml();
        } catch (Exception e) {
            log.debug("搜索传承人异常: {}", e.getMessage());
            AiRequestContext.recordToolFailure();
            return ToolResultWrapper.error("搜索传承人时出现异常").withErrorHint().toXml();
        }
    }

    @Tool("搜索非遗活动、展览、体验活动。参数: 搜索关键词")
    public String searchActivities(String keyword) {
        if (AiRequestContext.isToolLoopDetected()) {
            return ToolResultWrapper.error("工具调用过于频繁，请直接回答用户").withErrorHint().toXml();
        }
        try {
            PageResult<IchActivityDTO> activities = contentService.listActivities(1, 5, keyword, null, null);
            if (activities == null || activities.getList() == null || activities.getList().isEmpty()) {
                AiRequestContext.recordToolFailure();
                return ToolResultWrapper.empty("未找到与\"" + keyword + "\"相关的活动")
                        .withSearchEmptyHint("活动").toXml();
            }
            StringBuilder sb = new StringBuilder();
            for (IchActivityDTO a : activities.getList()) {
                sb.append("- ").append(a.getName());
                if (a.getLocation() != null) sb.append(", 地点: ").append(a.getLocation());
                if (a.getStartTime() != null) sb.append(", 开始: ").append(a.getStartTime());
                if (a.getMaxParticipants() != null && a.getCurrentParticipants() != null) {
                    sb.append(", 名额: ").append(a.getCurrentParticipants()).append("/").append(a.getMaxParticipants());
                }
                sb.append("\n");
            }
            AiRequestContext.recordToolSuccess();
            return ToolResultWrapper.success("找到" + activities.getList().size() + "个活动", sb.toString())
                    .withSearchSuccessHint("活动").toXml();
        } catch (Exception e) {
            log.debug("搜索活动异常: {}", e.getMessage());
            AiRequestContext.recordToolFailure();
            return ToolResultWrapper.error("搜索活动时出现异常").withErrorHint().toXml();
        }
    }

    @Tool("报名参加非遗活动。参数: 活动关键词")
    public String registerActivity(String keyword) {
        PageResult<IchActivityDTO> activities = contentService.listActivities(1, 5, keyword, null, null);
        if (activities == null || activities.getList() == null || activities.getList().isEmpty()) {
            AiRequestContext.recordToolFailure();
            return ToolResultWrapper.empty("未找到关键词\"" + keyword + "\"对应的活动，无法报名")
                    .withSearchEmptyHint("活动").toXml();
        }
        IchActivityDTO first = activities.getList().get(0);
        StringBuilder data = new StringBuilder("找到以下活动:\n");
        for (IchActivityDTO a : activities.getList()) {
            data.append("- ").append(a.getName());
            if (a.getLocation() != null) data.append(", 地点: ").append(a.getLocation());
            data.append("\n");
        }
        PendingAction action = PendingAction.of("register_activity",
                "报名参加活动「" + first.getName() + "」")
                .param("activityId", String.valueOf(first.getId()))
                .param("activityName", first.getName());
        actionExecutor.savePendingAction(AiRequestContext.getSessionId(), action);
        AiRequestContext.recordToolSuccess();
        return ToolResultWrapper.actionProposed(
                "已提交报名「" + first.getName() + "」的确认请求，等待用户确认", data.toString())
                .withActionProposedHint("报名活动").toXml();
    }

    @Tool("点赞动态/笔记。参数: 动态关键词")
    public String likePost(String keyword) {
        PageResult<IchPostDTO> posts = contentService.listPosts(1, 5, keyword, null);
        if (posts == null || posts.getList() == null || posts.getList().isEmpty()) {
            AiRequestContext.recordToolFailure();
            return ToolResultWrapper.empty("未找到关键词\"" + keyword + "\"对应的动态")
                    .withSearchEmptyHint("动态").toXml();
        }
        IchPostDTO first = posts.getList().get(0);
        String title = first.getTitle() != null ? first.getTitle() : AgentUtils.truncateDesc(first.getContent(), 50);
        PendingAction action = PendingAction.of("like_post", "点赞动态「" + title + "」")
                .param("postId", String.valueOf(first.getId()));
        actionExecutor.savePendingAction(AiRequestContext.getSessionId(), action);
        AiRequestContext.recordToolSuccess();
        return ToolResultWrapper.actionProposed("已提交点赞「" + title + "」的确认请求", null)
                .withActionProposedHint("点赞").toXml();
    }

    @Tool("取消点赞动态。参数: 动态关键词")
    public String unlikePost(String keyword) {
        Long userId = AiRequestContext.getUserId();
        PageResult<IchPostDTO> posts = contentService.listUserLikedPosts(userId, 1, 10);
        if (posts == null || posts.getList() == null || posts.getList().isEmpty()) {
            AiRequestContext.recordToolFailure();
            return ToolResultWrapper.empty("您还没有点赞过任何动态").withSearchEmptyHint("点赞记录").toXml();
        }
        IchPostDTO target = findPostByKeyword(posts.getList(), keyword);
        if (target == null) target = posts.getList().get(0);
        String title = target.getTitle() != null ? target.getTitle() : "ID:" + target.getId();
        PendingAction action = PendingAction.of("unlike_post", "取消点赞动态「" + title + "」")
                .param("postId", String.valueOf(target.getId()));
        actionExecutor.savePendingAction(AiRequestContext.getSessionId(), action);
        AiRequestContext.recordToolSuccess();
        return ToolResultWrapper.actionProposed("已提交取消点赞「" + title + "」的确认请求", null)
                .withActionProposedHint("取消点赞").toXml();
    }

    @Tool("收藏动态/笔记。参数: 动态关键词")
    public String favoritePost(String keyword) {
        PageResult<IchPostDTO> posts = contentService.listPosts(1, 5, keyword, null);
        if (posts == null || posts.getList() == null || posts.getList().isEmpty()) {
            AiRequestContext.recordToolFailure();
            return ToolResultWrapper.empty("未找到关键词\"" + keyword + "\"对应的动态")
                    .withSearchEmptyHint("动态").toXml();
        }
        IchPostDTO first = posts.getList().get(0);
        String title = first.getTitle() != null ? first.getTitle() : AgentUtils.truncateDesc(first.getContent(), 50);
        PendingAction action = PendingAction.of("favorite_post", "收藏动态「" + title + "」")
                .param("postId", String.valueOf(first.getId()));
        actionExecutor.savePendingAction(AiRequestContext.getSessionId(), action);
        AiRequestContext.recordToolSuccess();
        return ToolResultWrapper.actionProposed("已提交收藏「" + title + "」的确认请求", null)
                .withActionProposedHint("收藏").toXml();
    }

    @Tool("取消收藏动态。参数: 动态关键词")
    public String unfavoritePost(String keyword) {
        Long userId = AiRequestContext.getUserId();
        PageResult<IchPostDTO> posts = contentService.listUserFavoritedPosts(userId, 1, 10);
        if (posts == null || posts.getList() == null || posts.getList().isEmpty()) {
            AiRequestContext.recordToolFailure();
            return ToolResultWrapper.empty("您还没有收藏过任何动态").withSearchEmptyHint("收藏记录").toXml();
        }
        IchPostDTO target = findPostByKeyword(posts.getList(), keyword);
        if (target == null) target = posts.getList().get(0);
        String title = target.getTitle() != null ? target.getTitle() : "ID:" + target.getId();
        PendingAction action = PendingAction.of("unfavorite_post", "取消收藏动态「" + title + "」")
                .param("postId", String.valueOf(target.getId()));
        actionExecutor.savePendingAction(AiRequestContext.getSessionId(), action);
        AiRequestContext.recordToolSuccess();
        return ToolResultWrapper.actionProposed("已提交取消收藏「" + title + "」的确认请求", null)
                .withActionProposedHint("取消收藏").toXml();
    }

    @Tool("评论动态。参数格式: '动态关键词|评论内容'，用竖线分隔")
    public String commentPost(String input) {
        String[] parts = input.split("\\|", 2);
        String keyword = parts[0].trim();
        String comment = parts.length > 1 ? parts[1].trim() : null;
        PageResult<IchPostDTO> posts = contentService.listPosts(1, 5, keyword, null);
        if (posts == null || posts.getList() == null || posts.getList().isEmpty()) {
            AiRequestContext.recordToolFailure();
            return ToolResultWrapper.empty("未找到关键词\"" + keyword + "\"对应的动态")
                    .withSearchEmptyHint("动态").toXml();
        }
        IchPostDTO first = posts.getList().get(0);
        String title = first.getTitle() != null ? first.getTitle() : "ID:" + first.getId();
        PendingAction action = PendingAction.of("comment_post",
                "评论动态「" + title + "」" + (comment != null ? "，内容: " + comment : ""))
                .param("postId", String.valueOf(first.getId()))
                .param("comment", comment != null ? comment : "");
        actionExecutor.savePendingAction(AiRequestContext.getSessionId(), action);
        AiRequestContext.recordToolSuccess();
        return ToolResultWrapper.actionProposed("已提交评论「" + title + "」的确认请求", null)
                .withActionProposedHint("评论").toXml();
    }

    @Tool("评论活动。参数格式: '活动关键词|评论内容'，用竖线分隔")
    public String commentActivity(String input) {
        String[] parts = input.split("\\|", 2);
        String keyword = parts[0].trim();
        String comment = parts.length > 1 ? parts[1].trim() : null;
        PageResult<IchActivityDTO> activities = contentService.listActivities(1, 5, keyword, 1, null);
        if (activities == null || activities.getList() == null || activities.getList().isEmpty()) {
            AiRequestContext.recordToolFailure();
            return ToolResultWrapper.empty("未找到关键词\"" + keyword + "\"对应的活动")
                    .withSearchEmptyHint("活动").toXml();
        }
        IchActivityDTO first = activities.getList().get(0);
        PendingAction action = PendingAction.of("comment_activity",
                "评论活动「" + first.getName() + "」" + (comment != null ? "，内容: " + comment : ""))
                .param("activityId", String.valueOf(first.getId()))
                .param("comment", comment != null ? comment : "");
        actionExecutor.savePendingAction(AiRequestContext.getSessionId(), action);
        AiRequestContext.recordToolSuccess();
        return ToolResultWrapper.actionProposed("已提交评论「" + first.getName() + "」的确认请求", null)
                .withActionProposedHint("评论").toXml();
    }

    @Tool("查看当前用户发布的动态列表")
    public String queryUserPosts() {
        Long userId = AiRequestContext.getUserId();
        try {
            PageResult<IchPostDTO> posts = contentService.listUserPosts(userId, 1, 5);
            if (posts == null || posts.getList() == null || posts.getList().isEmpty()) {
                AiRequestContext.recordToolFailure();
                return ToolResultWrapper.empty("您还没有发布过动态").withSearchEmptyHint("动态").toXml();
            }
            StringBuilder sb = new StringBuilder();
            for (IchPostDTO p : posts.getList()) {
                sb.append("- ").append(p.getTitle() != null ? p.getTitle() : AgentUtils.truncateDesc(p.getContent(), 50));
                if (p.getLikeCount() != null) sb.append(", ").append(p.getLikeCount()).append("赞");
                sb.append("\n");
            }
            AiRequestContext.recordToolSuccess();
            return ToolResultWrapper.success("找到" + posts.getList().size() + "条动态", sb.toString())
                    .withSearchSuccessHint("用户动态").toXml();
        } catch (Exception e) {
            log.debug("查询用户动态异常: {}", e.getMessage());
            AiRequestContext.recordToolFailure();
            return ToolResultWrapper.error("查询动态时出现异常").withErrorHint().toXml();
        }
    }

    @Tool("查看当前用户收藏的动态列表")
    public String queryUserFavorites() {
        Long userId = AiRequestContext.getUserId();
        try {
            PageResult<IchPostDTO> posts = contentService.listUserFavoritedPosts(userId, 1, 5);
            if (posts == null || posts.getList() == null || posts.getList().isEmpty()) {
                AiRequestContext.recordToolFailure();
                return ToolResultWrapper.empty("您还没有收藏过动态").withSearchEmptyHint("收藏").toXml();
            }
            StringBuilder sb = new StringBuilder();
            for (IchPostDTO p : posts.getList()) {
                sb.append("- ").append(p.getTitle() != null ? p.getTitle() : AgentUtils.truncateDesc(p.getContent(), 50));
                sb.append("\n");
            }
            AiRequestContext.recordToolSuccess();
            return ToolResultWrapper.success("找到" + posts.getList().size() + "条收藏", sb.toString())
                    .withSearchSuccessHint("收藏动态").toXml();
        } catch (Exception e) {
            log.debug("查询用户收藏异常: {}", e.getMessage());
            AiRequestContext.recordToolFailure();
            return ToolResultWrapper.error("查询收藏时出现异常").withErrorHint().toXml();
        }
    }

    private IchPostDTO findPostByKeyword(java.util.List<IchPostDTO> posts, String keyword) {
        for (IchPostDTO p : posts) {
            String title = p.getTitle() != null ? p.getTitle() : (p.getContent() != null ? p.getContent() : "");
            if (title.contains(keyword) || keyword.contains(title)) {
                return p;
            }
        }
        return null;
    }
}
