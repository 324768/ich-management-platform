package com.hyang.ich.omnitrix.infrastructure.telemetry;

import java.util.UUID;

/**
 * 线程本地追踪上下文，维护当前请求的 traceId 和 spanId 层级关系。
 *
 * 使用方式：
 * 1. 请求入口调用 TraceContext.start(traceId) 创建根 Span
 * 2. 进入子操作前调用 TraceContext.push(spanId) 压入子 Span
 * 3. 子操作结束后调用 TraceContext.pop() 回到父 Span
 * 4. 请求结束时调用 TraceContext.clear() 清理
 */
public class TraceContext {

    private static final ThreadLocal<String> TRACE_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_SPAN_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> PARENT_SPAN_ID = new ThreadLocal<>();

    /** 初始化追踪上下文（请求入口调用） */
    public static String start(String traceId) {
        String rootSpanId = generateSpanId();
        TRACE_ID.set(traceId);
        CURRENT_SPAN_ID.set(rootSpanId);
        PARENT_SPAN_ID.set(null);
        return rootSpanId;
    }

    /** 压入子 Span（进入 Agent/Tool/LLM 前调用） */
    public static String push() {
        String newSpanId = generateSpanId();
        PARENT_SPAN_ID.set(CURRENT_SPAN_ID.get());
        CURRENT_SPAN_ID.set(newSpanId);
        return newSpanId;
    }

    /** 弹出子 Span（Agent/Tool/LLM 结束后调用） */
    public static void pop() {
        CURRENT_SPAN_ID.set(PARENT_SPAN_ID.get());
        PARENT_SPAN_ID.set(null);
    }

    public static String getTraceId() {
        return TRACE_ID.get();
    }

    public static String getCurrentSpanId() {
        return CURRENT_SPAN_ID.get();
    }

    public static String getParentSpanId() {
        return PARENT_SPAN_ID.get();
    }

    /** 清理上下文（请求结束时必须调用，防止内存泄漏） */
    public static void clear() {
        TRACE_ID.remove();
        CURRENT_SPAN_ID.remove();
        PARENT_SPAN_ID.remove();
    }

    /** 判断当前线程是否有活跃的追踪上下文 */
    public static boolean isActive() {
        return TRACE_ID.get() != null;
    }

    private static String generateSpanId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
