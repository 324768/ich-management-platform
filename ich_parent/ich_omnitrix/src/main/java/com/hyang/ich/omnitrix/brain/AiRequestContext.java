package com.hyang.ich.omnitrix.brain;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 请求上下文 — ThreadLocal 持有当前请求的 userId / sessionId。
 * <p>
 * LangChain4j 的 @Tool 方法无法接收自定义上下文参数，
 * 因此通过 ThreadLocal 在 OrchestratorService 入口设置，在 Tool 方法内读取。
 * <p>
 * 跨线程传播：流式模式下 LangChain4j 的工具回调在 OkHttp 线程执行，
 * 与设置 ThreadLocal 的异步线程不同，因此增加 ConcurrentHashMap 作为降级查找。
 */
public final class AiRequestContext {

    private static final ThreadLocal<Context> HOLDER = new ThreadLocal<>();

    /** 跨线程上下文映射：userId -> Context，用于 OkHttp 回调线程访问 */
    private static final ConcurrentHashMap<Long, Context> CROSS_THREAD_MAP = new ConcurrentHashMap<>();

    /** 连续空/错误结果阈值 — 超过此值时 @Tool 方法应返回强制停止 Hint */
    public static final int MAX_CONSECUTIVE_FAILURES = 3;

    public static void set(Long userId, String sessionId) {
        set(userId, sessionId, null);
    }

    public static void set(Long userId, String sessionId, SseEmitter emitter) {
        Context ctx = new Context(userId, sessionId, emitter);
        HOLDER.set(ctx);
        if (userId != null) {
            CROSS_THREAD_MAP.put(userId, ctx);
        }
    }

    public static Long getUserId() {
        Context ctx = getEffectiveContext();
        return ctx != null ? ctx.userId : null;
    }

    public static String getSessionId() {
        Context ctx = getEffectiveContext();
        return ctx != null ? ctx.sessionId : null;
    }

    public static SseEmitter getEmitter() {
        Context ctx = getEffectiveContext();
        return ctx != null ? ctx.emitter : null;
    }

    /**
     * 获取有效上下文：优先 ThreadLocal，降级查找 CROSS_THREAD_MAP。
     * 解决流式模式下 LangChain4j 工具在 OkHttp 回调线程执行时 ThreadLocal 为空的问题。
     */
    private static Context getEffectiveContext() {
        Context ctx = HOLDER.get();
        if (ctx != null) return ctx;
        // 降级：从跨线程 map 查找（适用于单并发场景）
        if (CROSS_THREAD_MAP.size() == 1) {
            return CROSS_THREAD_MAP.values().iterator().next();
        }
        return null;
    }

    /** 记录一次成功的工具调用（重置连续失败计数） */
    public static void recordToolSuccess() {
        Context ctx = getEffectiveContext();
        if (ctx != null) {
            ctx.totalToolCalls++;
            ctx.consecutiveFailures = 0;
        }
    }

    /** 记录一次失败/空结果的工具调用 */
    public static void recordToolFailure() {
        Context ctx = getEffectiveContext();
        if (ctx != null) {
            ctx.totalToolCalls++;
            ctx.consecutiveFailures++;
        }
    }

    /** 获取连续失败次数 */
    public static int getConsecutiveFailures() {
        Context ctx = getEffectiveContext();
        return ctx != null ? ctx.consecutiveFailures : 0;
    }

    /** 获取总工具调用次数 */
    public static int getTotalToolCalls() {
        Context ctx = getEffectiveContext();
        return ctx != null ? ctx.totalToolCalls : 0;
    }

    /** 是否已达到连续失败上限 */
    public static boolean isToolLoopDetected() {
        return getConsecutiveFailures() >= MAX_CONSECUTIVE_FAILURES;
    }

    public static void clear() {
        Context ctx = HOLDER.get();
        if (ctx != null && ctx.userId != null) {
            CROSS_THREAD_MAP.remove(ctx.userId);
        }
        HOLDER.remove();
    }

    public static void clearLocal() {
        HOLDER.remove();
    }

    /** 跨线程清理（在流式回调 onComplete/onError 中调用） */
    public static void clearCrossThread(Long userId) {
        if (userId != null) {
            CROSS_THREAD_MAP.remove(userId);
        }
    }

    private static final class Context {
        final Long userId;
        final String sessionId;
        final SseEmitter emitter;
        int totalToolCalls;
        int consecutiveFailures;

        Context(Long userId, String sessionId, SseEmitter emitter) {
            this.userId = userId;
            this.sessionId = sessionId;
            this.emitter = emitter;
        }
    }
}
