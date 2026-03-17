package com.hyang.ich.omnitrix.brain;

/**
 * AI 请求上下文 — ThreadLocal 持有当前请求的 userId / sessionId。
 * <p>
 * LangChain4j 的 @Tool 方法无法接收自定义上下文参数，
 * 因此通过 ThreadLocal 在 OrchestratorService 入口设置，在 Tool 方法内读取。
 */
public final class AiRequestContext {

    private static final ThreadLocal<Context> HOLDER = new ThreadLocal<>();

    /** 连续空/错误结果阈值 — 超过此值时 @Tool 方法应返回强制停止 Hint */
    public static final int MAX_CONSECUTIVE_FAILURES = 3;

    public static void set(Long userId, String sessionId) {
        HOLDER.set(new Context(userId, sessionId));
    }

    public static Long getUserId() {
        Context ctx = HOLDER.get();
        return ctx != null ? ctx.userId : null;
    }

    public static String getSessionId() {
        Context ctx = HOLDER.get();
        return ctx != null ? ctx.sessionId : null;
    }

    /** 记录一次成功的工具调用（重置连续失败计数） */
    public static void recordToolSuccess() {
        Context ctx = HOLDER.get();
        if (ctx != null) {
            ctx.totalToolCalls++;
            ctx.consecutiveFailures = 0;
        }
    }

    /** 记录一次失败/空结果的工具调用 */
    public static void recordToolFailure() {
        Context ctx = HOLDER.get();
        if (ctx != null) {
            ctx.totalToolCalls++;
            ctx.consecutiveFailures++;
        }
    }

    /** 获取连续失败次数 */
    public static int getConsecutiveFailures() {
        Context ctx = HOLDER.get();
        return ctx != null ? ctx.consecutiveFailures : 0;
    }

    /** 获取总工具调用次数 */
    public static int getTotalToolCalls() {
        Context ctx = HOLDER.get();
        return ctx != null ? ctx.totalToolCalls : 0;
    }

    /** 是否已达到连续失败上限 */
    public static boolean isToolLoopDetected() {
        return getConsecutiveFailures() >= MAX_CONSECUTIVE_FAILURES;
    }

    public static void clear() {
        HOLDER.remove();
    }

    private static final class Context {
        final Long userId;
        final String sessionId;
        int totalToolCalls;
        int consecutiveFailures;

        Context(Long userId, String sessionId) {
            this.userId = userId;
            this.sessionId = sessionId;
        }
    }
}
