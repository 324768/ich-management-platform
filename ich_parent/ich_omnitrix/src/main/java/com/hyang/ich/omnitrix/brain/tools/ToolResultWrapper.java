package com.hyang.ich.omnitrix.brain.tools;

/**
 * 工具结果包装器 — 参考 Kortex SubAgentResultWrapper 设计。
 * <p>
 * 将 @Tool 方法的返回值包装为结构化 XML 格式，引导 MasterBrain 做出正确决策：
 * - status: 结果状态 (SUCCESS / EMPTY / ERROR / ACTION_PROPOSED)
 * - summary: 摘要（MasterBrain 基于此决策）
 * - data: 完整数据（可选，仅在 show_result_to_user 时直推前端）
 * - hint: ACTION/REASON/DO_NOT 三元组，零额外 LLM 调用引导 MasterBrain
 */
public class ToolResultWrapper {

    public enum Status {
        SUCCESS, EMPTY, ERROR, ACTION_PROPOSED
    }

    private final Status status;
    private final String summary;
    private final String data;
    private String actionHint;
    private String reasonHint;
    private String doNotHint;

    private ToolResultWrapper(Status status, String summary, String data) {
        this.status = status;
        this.summary = summary;
        this.data = data;
    }

    // ========== 静态工厂 ==========

    /** 成功结果，带完整数据 */
    public static ToolResultWrapper success(String summary, String data) {
        return new ToolResultWrapper(Status.SUCCESS, summary, data);
    }

    /** 成功结果，无额外数据 */
    public static ToolResultWrapper success(String summary) {
        return new ToolResultWrapper(Status.SUCCESS, summary, null);
    }

    /** 空结果 */
    public static ToolResultWrapper empty(String summary) {
        return new ToolResultWrapper(Status.EMPTY, summary, null);
    }

    /** 错误结果 */
    public static ToolResultWrapper error(String summary) {
        return new ToolResultWrapper(Status.ERROR, summary, null);
    }

    /** 写操作待确认 */
    public static ToolResultWrapper actionProposed(String summary, String data) {
        return new ToolResultWrapper(Status.ACTION_PROPOSED, summary, data);
    }

    // ========== Hint 设置 ==========

    /** 设置 ACTION/REASON/DO_NOT 三元组 Hint */
    public ToolResultWrapper withHints(String action, String reason, String doNot) {
        this.actionHint = action;
        this.reasonHint = reason;
        this.doNotHint = doNot;
        return this;
    }

    // ========== 预定义 Hint 模板 ==========

    /** 搜索类工具：成功时的标准 Hint */
    public ToolResultWrapper withSearchSuccessHint(String domain) {
        return withHints(
                "基于以上" + domain + "数据，用自然语言回答用户问题",
                "用户想了解" + domain + "相关信息",
                "不要编造数据中没有的信息，不要重复调用相同的搜索工具"
        );
    }

    /** 搜索类工具：空结果时的标准 Hint */
    public ToolResultWrapper withSearchEmptyHint(String domain) {
        return withHints(
                "告知用户未找到匹配的" + domain + "，建议换个关键词或缩小范围",
                "当前关键词未匹配到结果",
                "不要编造不存在的" + domain + "数据，不要重复用相同关键词搜索"
        );
    }

    /** 写操作待确认时的标准 Hint */
    public ToolResultWrapper withActionProposedHint(String operationName) {
        return withHints(
                "将操作详情展示给用户，等待用户确认或取消",
                "写操作需要用户明确确认后才能执行",
                "不要自动确认操作，不要跳过确认流程，不要继续调用其他工具"
        );
    }

    /** 错误时的标准 Hint */
    public ToolResultWrapper withErrorHint() {
        return withHints(
                "告知用户操作遇到问题，建议稍后重试",
                "工具执行过程中发生异常",
                "不要重复调用刚才失败的工具，不要编造结果"
        );
    }

    // ========== 序列化为结构化 XML ==========

    /** 输出为结构化 XML 字符串，作为 @Tool 方法的 return 值 */
    public String toXml() {
        StringBuilder sb = new StringBuilder();
        sb.append("<tool_result>\n");
        sb.append("  <status>").append(status.name()).append("</status>\n");
        sb.append("  <summary>").append(escapeXml(summary)).append("</summary>\n");
        if (data != null && !data.isEmpty()) {
            sb.append("  <data>").append(escapeXml(data)).append("</data>\n");
        }
        if (actionHint != null || reasonHint != null || doNotHint != null) {
            sb.append("  <hint>\n");
            if (actionHint != null) sb.append("    <action>").append(escapeXml(actionHint)).append("</action>\n");
            if (reasonHint != null) sb.append("    <reason>").append(escapeXml(reasonHint)).append("</reason>\n");
            if (doNotHint != null) sb.append("    <do_not>").append(escapeXml(doNotHint)).append("</do_not>\n");
            sb.append("  </hint>\n");
        }
        sb.append("</tool_result>");
        return sb.toString();
    }

    @Override
    public String toString() {
        return toXml();
    }

    private static String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
