package com.hyang.ich.omnitrix.brain.subbrain;

/**
 * SubBrain 结果包装器 — 参考 Kortex SubAgentResultWrapper 设计。
 * <p>
 * 将子脑的完整 LLM 回复包装为结构化 XML，引导 Ultra MasterBrain 做出正确决策。
 * 纯字符串操作，零额外 LLM 调用。
 * <p>
 * 输出格式：
 * <pre>
 * &lt;sub_brain_result status="SUCCESS" brain="user_ai" call_number="1/5"&gt;
 *   &lt;summary&gt;...摘要...&lt;/summary&gt;
 *   &lt;data&gt;...原始数据（截断）...&lt;/data&gt;
 *   &lt;hint&gt;ACTION/REASON/DO_NOT 三元组&lt;/hint&gt;
 * &lt;/sub_brain_result&gt;
 * </pre>
 */
public class SubBrainResultWrapper {

    /** 子脑结果状态 */
    public enum Status {
        SUCCESS, EMPTY, ERROR, ACTION_PROPOSED
    }

    /** 数据截断阈值（字符数），避免塞满 Ultra 的上下文窗口 */
    private static final int MAX_DATA_LENGTH = 1500;
    /** 摘要截取长度 */
    private static final int MAX_SUMMARY_LENGTH = 200;

    /**
     * 包装子脑执行结果为结构化 XML
     *
     * @param brainCode   子脑代码
     * @param result      子脑执行结果
     * @param callNumber  本次调用序号（用于 Hint 中的调用预算感知）
     * @param maxCalls    最大调用次数
     * @return 结构化 XML 字符串
     */
    public static String wrap(String brainCode, SubBrainResult result,
                              int callNumber, int maxCalls) {
        String rawContent = result.getContent();
        boolean hasPendingAction = result.isHasPendingAction();

        Status status = analyzeStatus(rawContent, hasPendingAction);
        String summary = generateSummary(rawContent, hasPendingAction, result.getPendingActionDescription());
        String hint = generateHint(status, brainCode, callNumber, maxCalls);

        StringBuilder sb = new StringBuilder();
        sb.append("<sub_brain_result status=\"").append(status.name())
                .append("\" brain=\"").append(brainCode)
                .append("\" call_number=\"").append(callNumber).append("/").append(maxCalls)
                .append("\">\n");
        sb.append("  <summary>").append(escapeXml(summary)).append("</summary>\n");

        if (rawContent != null && !rawContent.isEmpty()) {
            String data = rawContent.length() > MAX_DATA_LENGTH
                    ? rawContent.substring(0, MAX_DATA_LENGTH) + "...(已截断)"
                    : rawContent;
            sb.append("  <data>").append(escapeXml(data)).append("</data>\n");
        }

        if (hasPendingAction && result.getPendingActionDescription() != null) {
            sb.append("  <pending_action>").append(escapeXml(result.getPendingActionDescription()))
                    .append("</pending_action>\n");
        }

        sb.append("  <hint>\n    ").append(hint).append("\n  </hint>\n");
        sb.append("</sub_brain_result>");
        return sb.toString();
    }

    /**
     * 分析子脑返回状态 — 参考 Kortex analyzeStatus 的优先级检测
     */
    private static Status analyzeStatus(String rawContent, boolean hasPendingAction) {
        if (rawContent == null || rawContent.isEmpty()) {
            return Status.EMPTY;
        }
        if (hasPendingAction) {
            return Status.ACTION_PROPOSED;
        }
        String lower = rawContent.toLowerCase();
        if (lower.contains("[子脑执行失败]") || lower.contains("[操作失败]")) {
            return Status.ERROR;
        }
        // 检测空结果（子脑 LLM 可能用自然语言表达"未找到"）
        if (rawContent.length() < 100) {
            if (lower.contains("未找到") || lower.contains("暂无") || lower.contains("为空")
                    || lower.contains("没有找到") || lower.contains("暂未收录")) {
                return Status.EMPTY;
            }
        }
        return Status.SUCCESS;
    }

    /**
     * 生成摘要
     */
    private static String generateSummary(String rawContent, boolean hasPendingAction,
                                           String pendingActionDesc) {
        if (hasPendingAction && pendingActionDesc != null) {
            return pendingActionDesc;
        }
        if (rawContent == null || rawContent.isEmpty()) {
            return "子脑未返回结果";
        }
        if (rawContent.length() <= MAX_SUMMARY_LENGTH) {
            return rawContent;
        }
        return rawContent.substring(0, MAX_SUMMARY_LENGTH) + "...";
    }

    /**
     * 生成动态 Hint — 参考 Kortex 的 ACTION/REASON/DO_NOT 三元组。
     * 基于状态 + 调用次数动态生成决策建议。
     */
    private static String generateHint(Status status, String brainCode,
                                        int callNumber, int maxCalls) {
        StringBuilder hint = new StringBuilder();

        switch (status) {
            case ACTION_PROPOSED:
                hint.append("ACTION: relay_confirmation_to_user\n");
                hint.append("    REASON: ").append(brainCode).append(" 子脑已提出写操作确认请求，需要转述给用户等待确认\n");
                hint.append("    DO_NOT: 不要自动确认操作，不要跳过确认流程，不要继续调用其他子脑");
                break;

            case SUCCESS:
                if (callNumber >= maxCalls - 1) {
                    hint.append("ACTION: reply_with_available_info\n");
                    hint.append("    REASON: 子脑调用次数即将耗尽（").append(callNumber).append("/").append(maxCalls).append("），用已有信息整合回复\n");
                    hint.append("    DO_NOT: 不要再调用子脑，直接整合已有结果回复用户");
                } else {
                    hint.append("ACTION: reply_to_user_or_continue\n");
                    hint.append("    REASON: ").append(brainCode).append(" 子脑已成功返回结果\n");
                    hint.append("    DO_NOT: 不要重复调用同一个子脑执行相同查询，不要编造结果中没有的数据");
                }
                break;

            case EMPTY:
                hint.append("ACTION: inform_user_no_result\n");
                hint.append("    REASON: ").append(brainCode).append(" 子脑未找到匹配数据\n");
                hint.append("    DO_NOT: 不要编造不存在的数据，可以尝试换个关键词重新调用或基于知识回答");
                break;

            case ERROR:
                hint.append("ACTION: report_error_to_user\n");
                hint.append("    REASON: ").append(brainCode).append(" 子脑执行出错\n");
                hint.append("    DO_NOT: 不要重复调用刚才失败的子脑，不要编造结果");
                break;

            default:
                hint.append("ACTION: reply_to_user\n");
                hint.append("    REASON: 默认处理\n");
                hint.append("    DO_NOT: 不要过度解读结果");
        }

        return hint.toString();
    }

    private static String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
