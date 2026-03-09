package com.hyang.ich.omnitrix.infrastructure.llm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * LLM 服务熔断器：连续失败达到阈值后进入 OPEN 状态，拒绝请求一段时间，
 * 之后进入 HALF_OPEN 状态允许试探请求，成功则恢复 CLOSED。
 *
 * 2025 趋势：AI 应用必须对 LLM 服务不稳定做弹性处理，
 * 避免连锁超时拖垮整个系统。
 */
@Slf4j
@Component
public class LlmCircuitBreaker {

    public enum State { CLOSED, OPEN, HALF_OPEN }

    /** 连续失败次数阈值，达到后熔断 */
    private static final int FAILURE_THRESHOLD = 5;
    /** 熔断持续时间（毫秒） */
    private static final long OPEN_DURATION_MS = 30_000L; // 30 秒
    /** 半开状态允许的试探请求数 */
    private static final int HALF_OPEN_PERMITS = 2;

    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicLong openedAt = new AtomicLong(0);
    private final AtomicInteger halfOpenAttempts = new AtomicInteger(0);
    private volatile State state = State.CLOSED;

    /**
     * 检查是否允许发起请求
     * @return true = 允许请求, false = 熔断拒绝
     */
    public boolean allowRequest() {
        switch (state) {
            case CLOSED:
                return true;
            case OPEN:
                if (System.currentTimeMillis() - openedAt.get() > OPEN_DURATION_MS) {
                    toHalfOpen();
                    return halfOpenAttempts.incrementAndGet() <= HALF_OPEN_PERMITS;
                }
                return false;
            case HALF_OPEN:
                return halfOpenAttempts.incrementAndGet() <= HALF_OPEN_PERMITS;
            default:
                return true;
        }
    }

    /**
     * 记录请求成功
     */
    public void recordSuccess() {
        if (state != State.CLOSED) {
            log.info("LLM 熔断器恢复: {} → CLOSED", state);
        }
        consecutiveFailures.set(0);
        halfOpenAttempts.set(0);
        state = State.CLOSED;
    }

    /**
     * 记录请求失败
     */
    public void recordFailure() {
        int failures = consecutiveFailures.incrementAndGet();
        if (state == State.HALF_OPEN) {
            toOpen();
            log.warn("LLM 熔断器半开试探失败，重新熔断: failures={}", failures);
        } else if (failures >= FAILURE_THRESHOLD && state == State.CLOSED) {
            toOpen();
            log.warn("LLM 熔断器触发: 连续失败 {} 次，进入 OPEN 状态 {}s",
                    failures, OPEN_DURATION_MS / 1000);
        }
    }

    public State getState() {
        return state;
    }

    private void toOpen() {
        state = State.OPEN;
        openedAt.set(System.currentTimeMillis());
        halfOpenAttempts.set(0);
    }

    private void toHalfOpen() {
        state = State.HALF_OPEN;
        halfOpenAttempts.set(0);
        log.info("LLM 熔断器进入 HALF_OPEN 状态，允许试探请求");
    }
}
