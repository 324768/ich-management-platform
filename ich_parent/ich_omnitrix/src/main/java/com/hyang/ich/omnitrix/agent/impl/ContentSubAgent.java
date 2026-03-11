package com.hyang.ich.omnitrix.agent.impl;

import com.hyang.ich.common.vo.PageResult;
import com.hyang.ich.content.ContentService;
import com.hyang.ich.content.dto.IchActivityDTO;
import com.hyang.ich.content.dto.IchItemDTO;
import com.hyang.ich.content.dto.IchHeritageManDTO;
import com.hyang.ich.content.dto.IchPostDTO;
import com.hyang.ich.omnitrix.agent.AgentContext;
import com.hyang.ich.omnitrix.agent.AgentUtils;
import com.hyang.ich.omnitrix.agent.SubAgent;
import com.hyang.ich.omnitrix.agent.tool.AgentTool;
import com.hyang.ich.omnitrix.agent.tool.ToolCallResult;
import com.hyang.ich.omnitrix.agent.tool.HintGenerator;
import com.hyang.ich.omnitrix.agent.tool.ToolSelector;
import com.hyang.ich.omnitrix.dto.AgentQueryResult;
import com.hyang.ich.omnitrix.dto.PendingAction;
import com.hyang.ich.omnitrix.infrastructure.prompt.HeritageSkillPrompt;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
public class ContentSubAgent implements SubAgent {

    private final ContentService contentService;
    private final ToolSelector toolSelector;

    private static final List<AgentTool> TOOLS = Arrays.asList(
            AgentTool.of("search_items", "搜索非遗项目、非遗文化、传统技艺", "搜索关键词"),
            AgentTool.of("search_heritage_men", "搜索传承人、非遗大师、手艺人", "搜索关键词"),
            AgentTool.of("search_all", "同时搜索非遗项目和传承人", "搜索关键词"),
            AgentTool.of("search_activities", "搜索非遗活动、展览、体验活动、文化活动", "搜索关键词"),
            AgentTool.of("register_activity", "报名参加活动、我要参加、帮我报名", "活动关键词"),
            AgentTool.of("comment_activity", "评论活动、给活动留言", "活动关键词和评论内容"),
            AgentTool.of("like_post", "点赞动态、点赞笔记、赞一下", "动态关键词"),
            AgentTool.of("unlike_post", "取消点赞动态、取消赞", "动态关键词"),
            AgentTool.of("favorite_post", "收藏动态、收藏笔记、收藏这个", "动态关键词"),
            AgentTool.of("unfavorite_post", "取消收藏动态、不收藏了", "动态关键词"),
            AgentTool.of("comment_post", "评论动态、给动态留言、发评论", "动态关键词和评论内容"),
            AgentTool.of("query_user_posts", "查看我的动态、我发布的笔记", "无"),
            AgentTool.of("query_user_favorites", "查看我收藏的动态、我的收藏", "无")
    );

    public ContentSubAgent(ContentService contentService, ToolSelector toolSelector) {
        this.contentService = contentService;
        this.toolSelector = toolSelector;
    }

    @Override
    public String getCode() {
        return "content_assistant";
    }

    @Override
    public String getName() {
        return "非遗内容助手";
    }

    @Override
    public String getDescription() {
        return "回答非遗项目、传承人、活动相关问题";
    }

    @Override
    public String getAgentPrompt() {
        // 继承HeritageSkill基础
        String heritageSkill = HeritageSkillPrompt.getHeritageMasterPrompt();

        // 业务上下文 - Skill由主脑动态注入，不再这里静态引入
        String businessContext = "## 当前任务模式: 非遗内容助手\n" +
                "系统已为你查询了平台数据库中的相关信息，请基于 [查询结果] 回答。\n" +
                "- 如果查到了具体非遗项目/传承人/活动，请自然地融入回答\n" +
                "- 如果用户问的内容超出查询结果，可以结合你的知识补充，但要注明\"据我所知\"\n" +
                "- 提及平台中存在的项目时，可以引导用户\"您可以在平台上查看详情\"";

        return heritageSkill + "\n\n" + businessContext;
    }

    @Override
    public AgentQueryResult execute(String userQuery, AgentContext context) {
        try {
            ToolCallResult toolCall = toolSelector.select(userQuery, TOOLS);
            log.debug("ContentSubAgent 工具选择: tool={}, param={}", toolCall.getToolName(), toolCall.getParameter());

            String keyword = toolCall.getParameter() != null && !toolCall.getParameter().isEmpty()
                    ? toolCall.getParameter() : AgentUtils.extractKeyword(userQuery);

            String selectedTool = toolCall.isNone() ? fallbackTool(userQuery) : toolCall.getToolName();

            StringBuilder data = new StringBuilder();

            switch (selectedTool) {
                case "search_items":
                    data.append(searchItems(keyword));
                    break;
                case "search_heritage_men":
                    data.append(searchHeritageMen(keyword));
                    break;
                case "search_activities":
                    data.append(searchActivities(keyword));
                    break;
                case "register_activity":
                    return proposeRegisterActivity(keyword, context);
                case "comment_activity":
                    return proposeCommentActivity(keyword, userQuery, context);
                case "like_post":
                    return proposeLikePost(keyword, context);
                case "unlike_post":
                    return proposeUnlikePost(keyword, context);
                case "favorite_post":
                    return proposeFavoritePost(keyword, context);
                case "unfavorite_post":
                    return proposeUnfavoritePost(keyword, context);
                case "comment_post":
                    return proposeCommentPost(keyword, userQuery, context);
                case "query_user_posts":
                    data.append(queryUserPosts(context));
                    break;
                case "query_user_favorites":
                    data.append(queryUserFavorites(context));
                    break;
                case "search_all":
                default:
                    data.append(searchItems(keyword));
                    data.append(searchHeritageMen(keyword));
                    break;
            }

            if (data.length() == 0) {
                return HintGenerator.applyHints(AgentQueryResult.empty(getCode()), getCode(), selectedTool);
            }
            return HintGenerator.applyHints(
                    AgentQueryResult.success(data.toString(), getCode()), getCode(), selectedTool);

        } catch (Exception e) {
            log.error("ContentSubAgent 执行异常: {}", e.getMessage(), e);
            return AgentQueryResult.error(getCode(), e.getMessage());
        }
    }

    private String fallbackTool(String query) {
        String q = query.toLowerCase();
        if (q.contains("报名") || q.contains("参加活动") || q.contains("我要参加")) return "register_activity";
        if (q.contains("评论活动") || q.contains("给活动留言")) return "comment_activity";
        if (q.contains("取消点赞") || q.contains("取消赞")) return "unlike_post";
        if (q.contains("点赞")) return "like_post";
        if (q.contains("取消收藏") || q.contains("不收藏")) return "unfavorite_post";
        if (q.contains("收藏")) return "favorite_post";
        if (q.contains("评论动态") || q.contains("留言") || q.contains("发评论")) return "comment_post";
        if (q.contains("我的动态") || q.contains("我发布的")) return "query_user_posts";
        if (q.contains("我的收藏") || q.contains("收藏列表")) return "query_user_favorites";
        if (q.contains("活动") || q.contains("展览") || q.contains("体验")) return "search_activities";
        if (q.contains("传承人") || q.contains("大师")) return "search_heritage_men";
        return "search_all";
    }

    // ========== 写操作提议 ==========

    private AgentQueryResult proposeRegisterActivity(String keyword, AgentContext context) {
        PageResult<IchActivityDTO> activities = contentService.listActivities(1, 5, keyword, 1, null);
        if (activities == null || activities.getList() == null || activities.getList().isEmpty()) {
            return AgentQueryResult.success(
                    "未找到关键词\"" + keyword + "\"对应的活动", getCode());
        }
        IchActivityDTO first = activities.getList().get(0);
        StringBuilder data = new StringBuilder("活动搜索结果:\n");
        for (IchActivityDTO a : activities.getList()) {
            data.append("- ").append(a.getName());
            if (a.getLocation() != null) data.append(", 地点: ").append(a.getLocation());
            if (a.getStartTime() != null) data.append(", 开始: ").append(a.getStartTime());
            if (a.getMaxParticipants() != null && a.getCurrentParticipants() != null) {
                data.append(", 名额: ").append(a.getCurrentParticipants()).append("/").append(a.getMaxParticipants());
            }
            data.append("\n");
        }
        PendingAction action = PendingAction.of("register_activity",
                "报名参加活动「" + first.getName() + "」")
                .param("activityId", String.valueOf(first.getId()))
                .param("activityName", first.getName());
        return AgentQueryResult.actionProposed(data.toString(), getCode(), action);
    }

    private AgentQueryResult proposeLikePost(String keyword, AgentContext context) {
        PageResult<IchPostDTO> posts = contentService.listPosts(1, 5, keyword, null);
        if (posts == null || posts.getList() == null || posts.getList().isEmpty()) {
            return AgentQueryResult.success(
                    "未找到关键词\"" + keyword + "\"对应的动态", getCode());
        }
        IchPostDTO first = posts.getList().get(0);
        StringBuilder data = new StringBuilder("动态搜索结果:\n");
        for (IchPostDTO p : posts.getList()) {
            data.append("- ").append(p.getTitle() != null ? p.getTitle() : AgentUtils.truncateDesc(p.getContent(), 50));
            data.append("\n");
        }
        PendingAction action = PendingAction.of("like_post",
                "点赞动态「" + (first.getTitle() != null ? first.getTitle() : "ID:" + first.getId()) + "」")
                .param("postId", String.valueOf(first.getId()));
        return AgentQueryResult.actionProposed(data.toString(), getCode(), action);
    }

    private AgentQueryResult proposeFavoritePost(String keyword, AgentContext context) {
        PageResult<IchPostDTO> posts = contentService.listPosts(1, 5, keyword, null);
        if (posts == null || posts.getList() == null || posts.getList().isEmpty()) {
            return AgentQueryResult.success(
                    "未找到关键词\"" + keyword + "\"对应的动态", getCode());
        }
        IchPostDTO first = posts.getList().get(0);
        StringBuilder data = new StringBuilder("动态搜索结果:\n");
        for (IchPostDTO p : posts.getList()) {
            data.append("- ").append(p.getTitle() != null ? p.getTitle() : AgentUtils.truncateDesc(p.getContent(), 50));
            data.append("\n");
        }
        PendingAction action = PendingAction.of("favorite_post",
                "收藏动态「" + (first.getTitle() != null ? first.getTitle() : "ID:" + first.getId()) + "」")
                .param("postId", String.valueOf(first.getId()));
        return AgentQueryResult.actionProposed(data.toString(), getCode(), action);
    }

    private AgentQueryResult proposeUnlikePost(String keyword, AgentContext context) {
        PageResult<IchPostDTO> posts = contentService.listUserLikedPosts(context.getUserId(), 1, 10);
        if (posts == null || posts.getList() == null || posts.getList().isEmpty()) {
            return AgentQueryResult.success("您还没有点赞过任何动态", getCode());
        }
        IchPostDTO target = null;
        for (IchPostDTO p : posts.getList()) {
            String title = p.getTitle() != null ? p.getTitle() : (p.getContent() != null ? p.getContent() : "");
            if (title.contains(keyword) || keyword.contains(title)) {
                target = p;
                break;
            }
        }
        if (target == null) target = posts.getList().get(0);
        StringBuilder data = new StringBuilder("您点赞过的动态:\n");
        for (IchPostDTO p : posts.getList()) {
            data.append("- ").append(p.getTitle() != null ? p.getTitle() : AgentUtils.truncateDesc(p.getContent(), 50)).append("\n");
        }
        PendingAction action = PendingAction.of("unlike_post",
                "取消点赞动态「" + (target.getTitle() != null ? target.getTitle() : "ID:" + target.getId()) + "」")
                .param("postId", String.valueOf(target.getId()));
        return AgentQueryResult.actionProposed(data.toString(), getCode(), action);
    }

    private AgentQueryResult proposeUnfavoritePost(String keyword, AgentContext context) {
        PageResult<IchPostDTO> posts = contentService.listUserFavoritedPosts(context.getUserId(), 1, 10);
        if (posts == null || posts.getList() == null || posts.getList().isEmpty()) {
            return AgentQueryResult.success("您还没有收藏过任何动态", getCode());
        }
        IchPostDTO target = null;
        for (IchPostDTO p : posts.getList()) {
            String title = p.getTitle() != null ? p.getTitle() : (p.getContent() != null ? p.getContent() : "");
            if (title.contains(keyword) || keyword.contains(title)) {
                target = p;
                break;
            }
        }
        if (target == null) target = posts.getList().get(0);
        StringBuilder data = new StringBuilder("您收藏的动态:\n");
        for (IchPostDTO p : posts.getList()) {
            data.append("- ").append(p.getTitle() != null ? p.getTitle() : AgentUtils.truncateDesc(p.getContent(), 50)).append("\n");
        }
        PendingAction action = PendingAction.of("unfavorite_post",
                "取消收藏动态「" + (target.getTitle() != null ? target.getTitle() : "ID:" + target.getId()) + "」")
                .param("postId", String.valueOf(target.getId()));
        return AgentQueryResult.actionProposed(data.toString(), getCode(), action);
    }

    private AgentQueryResult proposeCommentActivity(String keyword, String userQuery, AgentContext context) {
        // 从关键词中提取搜索词（去掉评论内容部分）
        String searchKeyword = extractSearchKeyword(keyword);
        PageResult<IchActivityDTO> activities = contentService.listActivities(1, 5, searchKeyword, 1, null);
        if (activities == null || activities.getList() == null || activities.getList().isEmpty()) {
            return AgentQueryResult.success(
                    "未找到关键词\"" + searchKeyword + "\"对应的活动", getCode());
        }
        IchActivityDTO first = activities.getList().get(0);
        StringBuilder data = new StringBuilder("活动搜索结果:\n");
        for (IchActivityDTO a : activities.getList()) {
            data.append("- ").append(a.getName()).append("\n");
        }
        String commentText = extractCommentText(userQuery);
        PendingAction action = PendingAction.of("comment_activity",
                "评论活动「" + first.getName() + "」" + (commentText != null ? "，内容: " + commentText : ""))
                .param("activityId", String.valueOf(first.getId()))
                .param("comment", commentText != null ? commentText : "");
        return AgentQueryResult.actionProposed(data.toString(), getCode(), action);
    }

    private AgentQueryResult proposeCommentPost(String keyword, String userQuery, AgentContext context) {
        String searchKeyword = extractSearchKeyword(keyword);
        PageResult<IchPostDTO> posts = contentService.listPosts(1, 5, searchKeyword, null);
        if (posts == null || posts.getList() == null || posts.getList().isEmpty()) {
            return AgentQueryResult.success(
                    "未找到关键词\"" + searchKeyword + "\"对应的动态", getCode());
        }
        IchPostDTO first = posts.getList().get(0);
        StringBuilder data = new StringBuilder("动态搜索结果:\n");
        for (IchPostDTO p : posts.getList()) {
            data.append("- ").append(p.getTitle() != null ? p.getTitle() : AgentUtils.truncateDesc(p.getContent(), 50)).append("\n");
        }
        String commentText = extractCommentText(userQuery);
        PendingAction action = PendingAction.of("comment_post",
                "评论动态「" + (first.getTitle() != null ? first.getTitle() : "ID:" + first.getId()) + "」" +
                        (commentText != null ? "，内容: " + commentText : ""))
                .param("postId", String.valueOf(first.getId()))
                .param("comment", commentText != null ? commentText : "");
        return AgentQueryResult.actionProposed(data.toString(), getCode(), action);
    }

    // ========== 评论文本提取 ==========

    /** 从用户原始查询中提取评论内容（去掉指令词和目标名） */
    private String extractCommentText(String userQuery) {
        if (userQuery == null) return null;
        // 去掉常见指令词前缀
        String text = userQuery.replaceAll("(?i)(帮我|请|给|对|我要|我想).{0,2}(评论|留言|说)", "")
                .replaceAll("(评论活动|评论动态|给活动留言|给动态留言|发评论)", "")
                .trim();
        // 如果剩余内容太短或与原文一样，说明没有独立的评论内容
        if (text.isEmpty() || text.equals(userQuery.trim()) || text.length() < 2) {
            return null;
        }
        return text;
    }

    /** 从 keyword 中尝试分离出搜索词（去掉可能混入的评论内容） */
    private String extractSearchKeyword(String keyword) {
        if (keyword == null || keyword.isEmpty()) return keyword;
        // 如果包含常见分隔符，取第一段作为搜索词
        String[] parts = keyword.split("[,，;；\\s]+", 2);
        return parts[0].trim();
    }

    // ========== 只读查询 ==========

    private String queryUserPosts(AgentContext context) {
        try {
            PageResult<IchPostDTO> posts = contentService.listUserPosts(context.getUserId(), 1, 5);
            if (posts == null || posts.getList() == null || posts.getList().isEmpty()) {
                return "您还没有发布过动态\n";
            }
            StringBuilder sb = new StringBuilder("您发布的动态:\n");
            for (IchPostDTO p : posts.getList()) {
                sb.append("- ").append(p.getTitle() != null ? p.getTitle() : AgentUtils.truncateDesc(p.getContent(), 50));
                if (p.getLikeCount() != null) sb.append(", ").append(p.getLikeCount()).append("赞");
                sb.append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            log.debug("查询用户动态异常: {}", e.getMessage());
            return "";
        }
    }

    private String queryUserFavorites(AgentContext context) {
        try {
            PageResult<IchPostDTO> posts = contentService.listUserFavoritedPosts(context.getUserId(), 1, 5);
            if (posts == null || posts.getList() == null || posts.getList().isEmpty()) {
                return "您还没有收藏过动态\n";
            }
            StringBuilder sb = new StringBuilder("您收藏的动态:\n");
            for (IchPostDTO p : posts.getList()) {
                sb.append("- ").append(p.getTitle() != null ? p.getTitle() : AgentUtils.truncateDesc(p.getContent(), 50)).append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            log.debug("查询用户收藏异常: {}", e.getMessage());
            return "";
        }
    }

    private String searchActivities(String keyword) {
        try {
            PageResult<IchActivityDTO> activities = contentService.listActivities(1, 5, keyword, 1, null);
            if (activities == null || activities.getList() == null || activities.getList().isEmpty()) {
                return "";
            }
            StringBuilder sb = new StringBuilder("非遗活动搜索结果:\n");
            for (IchActivityDTO a : activities.getList()) {
                sb.append("- ").append(a.getName());
                if (a.getLocation() != null) sb.append(", 地点: ").append(a.getLocation());
                if (a.getStartTime() != null) sb.append(", 开始: ").append(a.getStartTime());
                if (a.getMaxParticipants() != null && a.getCurrentParticipants() != null) {
                    sb.append(", 名额: ").append(a.getCurrentParticipants()).append("/").append(a.getMaxParticipants());
                }
                sb.append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            log.debug("活动搜索异常(可忽略): {}", e.getMessage());
            return "";
        }
    }

    private String searchItems(String keyword) {
        PageResult<IchItemDTO> items = contentService.listItems(1, 5, null, keyword, 1);
        if (items == null || items.getList() == null || items.getList().isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("非遗项目搜索结果:\n");
        for (IchItemDTO item : items.getList()) {
            sb.append("- ").append(item.getName());
            if (item.getDescription() != null) {
                sb.append(" - ").append(AgentUtils.truncateDesc(item.getDescription(), 100));
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private String searchHeritageMen(String keyword) {
        try {
            PageResult<IchHeritageManDTO> heritageMen = contentService.listHeritageMan(1, 3, keyword, 1);
            if (heritageMen == null || heritageMen.getList() == null || heritageMen.getList().isEmpty()) {
                return "";
            }
            StringBuilder sb = new StringBuilder("\n相关传承人:\n");
            for (IchHeritageManDTO man : heritageMen.getList()) {
                sb.append("- ").append(man.getName());
                if (man.getTitle() != null) {
                    sb.append(" (").append(man.getTitle()).append(")");
                }
                sb.append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            log.debug("传承人搜索异常(可忽略): {}", e.getMessage());
            return "";
        }
    }
}
