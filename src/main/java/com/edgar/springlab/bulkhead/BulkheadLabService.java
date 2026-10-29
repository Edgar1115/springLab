package com.edgar.springlab.bulkhead;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 练习 AI-Meeting 同款 Semaphore Bulkhead：
 * 同时只允许两个模拟 AI 请求执行，其他请求立即拒绝。
 */
@Service
public class BulkheadLabService {

    private final Bulkhead bulkhead;

    // 只有真正进入原始 Callable 才计数；被 Bulkhead 拒绝时不会增加。
    private final AtomicInteger actualAiCalls = new AtomicInteger();
    private final AtomicInteger activeAiCalls = new AtomicInteger();
    private final AtomicInteger rejectedRequests = new AtomicInteger();
    private final AtomicInteger maxObservedActiveAiCalls = new AtomicInteger();

    public BulkheadLabService() {
        BulkheadConfig config = BulkheadConfig.custom()
            .maxConcurrentCalls(2)                        // 同一时间最多允许两个调用进入受保护的任务
            .maxWaitDuration(Duration.ZERO)               // 两个许可都被占用时，新请求能等待多久，这里设置0s
            .build();
        bulkhead = Bulkhead.of("spring-lab-ai-bulkhead", config);
    }

    public Map<String, Object> call(long durationMillis) {
        // 防止实验中误传过长等待时间：限制为 0～10000 毫秒。
        long safeDurationMillis = Math.max(0, Math.min(durationMillis, 10_000L));

        // 模拟真正的 AI 调用：占用许可，持续执行一段时间。
        Callable<String> originalAiCall = () -> {
            int executionNumber = actualAiCalls.incrementAndGet();
            int active = activeAiCalls.incrementAndGet();
            maxObservedActiveAiCalls.accumulateAndGet(active, Math::max);

            try {
                Thread.sleep(safeDurationMillis);
                return "模拟 AI 调用完成：第 " + executionNumber + " 次实际执行";
            } finally {
                activeAiCalls.decrementAndGet();
            }
        };

        // 和 CircuitBreaker 一样，decorateCallable 只是包装，未执行原始任务。
        Callable<String> guarded =
            Bulkhead.decorateCallable(bulkhead, originalAiCall);

        String outcome;
        String detail;
        try {
            detail = guarded.call();
            outcome = "SUCCESS";
        } catch (BulkheadFullException ex) {
            // maxConcurrentCalls 个许可均被占用，maxWaitDuration=ZERO → 立即拒绝。
            rejectedRequests.incrementAndGet();
            outcome = "REJECTED";
            detail = "Bulkhead 许可已满，未执行模拟 AI";
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            outcome = "INTERRUPTED";
            detail = "模拟 AI 调用被中断";
        } catch (Exception ex) {
            outcome = "FAILED";
            detail = ex.getMessage();
        }

        Map<String, Object> result = state();
        result.put("outcome", outcome);
        result.put("detail", detail);
        result.put("durationMillis", safeDurationMillis);
        return result;
    }

    public Map<String, Object> state() {
        Bulkhead.Metrics metrics = bulkhead.getMetrics();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("maxConcurrentCalls", metrics.getMaxAllowedConcurrentCalls());
        result.put("availableConcurrentCalls", metrics.getAvailableConcurrentCalls());
        result.put("activeAiCalls", activeAiCalls.get());
        result.put("actualAiCalls", actualAiCalls.get());
        result.put("rejectedRequests", rejectedRequests.get());
        result.put("maxObservedActiveAiCalls", maxObservedActiveAiCalls.get());
        return result;
    }

    public Map<String, Object> resetCounters() {
        if (activeAiCalls.get() != 0
            || bulkhead.getMetrics().getAvailableConcurrentCalls() != 2) {
            throw new IllegalStateException("仍有请求在执行，请在全部完成后重置");
        }
        actualAiCalls.set(0);
        rejectedRequests.set(0);
        maxObservedActiveAiCalls.set(0);
        return state();
    }
}
