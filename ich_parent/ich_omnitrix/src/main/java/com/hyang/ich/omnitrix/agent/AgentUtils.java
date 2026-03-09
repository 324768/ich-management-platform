package com.hyang.ich.omnitrix.agent;

/**
 * 子代理公共工具方法，消除重复代码。
 */
public final class AgentUtils {

    private AgentUtils() {}

    /** 通用停用词 */
    private static final String[] COMMON_STOP_WORDS = {
            "请", "帮我", "帮", "查", "查询", "搜索", "搜", "看看", "告诉我",
            "一下", "有没有", "有什么", "有哪些", "什么是", "介绍", "关于",
            "的", "了", "吗", "呢", "吧", "啊", "是", "我", "想", "要",
            "知道", "了解", "能", "可以"
    };

    /**
     * 从用户问句中提取关键词（去除常见停用词）
     */
    public static String extractKeyword(String userQuery) {
        if (userQuery == null || userQuery.trim().isEmpty()) {
            return "";
        }
        String keyword = userQuery;
        for (String sw : COMMON_STOP_WORDS) {
            keyword = keyword.replace(sw, "");
        }
        return keyword.trim().isEmpty() ? userQuery.trim() : keyword.trim();
    }

    /**
     * 格式化订单状态
     */
    public static String formatOrderStatus(Integer status) {
        if (status == null) return "未知";
        switch (status) {
            case 0: return "待付款";
            case 1: return "已付款";
            case 2: return "已发货";
            case 3: return "已完成";
            case 4: return "已取消";
            case 5: return "退货中";
            default: return "状态" + status;
        }
    }

    /**
     * 格式化资格认证状态
     */
    public static String formatQualificationStatus(Integer status) {
        if (status == null) return "未知";
        switch (status) {
            case 0: return "待审核";
            case 1: return "已通过";
            case 2: return "已驳回";
            default: return "状态" + status;
        }
    }

    /**
     * 截取描述文本
     */
    public static String truncateDesc(String desc, int maxLen) {
        if (desc == null) return "";
        return desc.length() > maxLen ? desc.substring(0, maxLen) + "..." : desc;
    }
}
