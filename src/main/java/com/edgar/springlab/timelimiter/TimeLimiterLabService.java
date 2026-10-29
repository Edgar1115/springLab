package com.edgar.springlab.timelimiter;

import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * TimeLimiter 独立实验：限定等待时间，观察超时后后台任务是否还在执行。
 * 与 AI-Meeting 相同：CompletableFuture.supplyAsync + decorateFutureSupplier。
 */
@Service
public class TimeLimiterLabService {

    private final ExecutorService aiIoExecutor = Executors.newFixedThreadPool(2, task -> {
        Thread thread = new Thread(task, "spring-lab-ai-io");
        thread.setDaemon(true);
        return thread;
    });
    private final TimeLimiter timeLimiter;

    private final AtomicInteger actualAiCalls = new AtomicInteger();
    private final AtomicInteger activeAiCalls = new AtomicInteger();
    private final AtomicInteger finishedAiCalls = new AtomicInteger();
    private final AtomicInteger interruptedAiCalls = new AtomicInteger();
    private final AtomicInteger timedOutRequests = new AtomicInteger();

    public TimeLimiterLabService() {
        TimeLimiterConfig config = TimeLimiterConfig.custom()
            .timeoutDuration(Duration.ofMillis(500))                // 最多等待异步任务500ms
            .cancelRunningFuture(true)                              // 发生超时时，尝试取消对应的future
            .build();
        timeLimiter = TimeLimiter.of("spring-lab-time-limiter", config);
    }

    public Map<String, Object> call(long durationMillis) {
        long safeDuration = Math.max(0, Math.min(5000, durationMillis));

        Callable<String> originalAiCall = () -> {
            int callNumber = actualAiCalls.incrementAndGet();
            activeAiCalls.incrementAndGet();
            try {
                Thread.sleep(safeDuration);
                finishedAiCalls.incrementAndGet();
                return "模拟 AI 请求 " + callNumber + " 完成";
            } catch (InterruptedException ex) {
                interruptedAiCalls.incrementAndGet();
                Thread.currentThread().interrupt();
                throw ex;
            } finally {
                activeAiCalls.decrementAndGet();
            }
        };

        // Supplier 懒创建 CompletableFuture：真正执行时才提交到专用线程池。
        Callable<String> decorated = TimeLimiter.decorateFutureSupplier(
            timeLimiter,
            () -> CompletableFuture.supplyAsync(() -> {
                try {
                    return originalAiCall.call();
                } catch (Exception ex) {
                    throw new CompletionException(ex);
                }
            }, aiIoExecutor)
        );

        String outcome;
        String detail;
        long startedAt = System.nanoTime();
        try {
            detail = decorated.call();
            outcome = "SUCCESS";
        } catch (TimeoutException ex) {
            timedOutRequests.incrementAndGet();
            detail = "调用方等待超过 500ms（后台任务不一定已停止）";
            outcome = "TIMEOUT";
        } catch (Exception ex) {
            outcome = "FAILED";
            detail = ex.getClass().getSimpleName() + ": " + ex.getMessage();
        }

        Map<String, Object> result = state();
        result.put("outcome", outcome);
        result.put("detail", detail);
        result.put("requestedDurationMillis", safeDuration);
        result.put("elapsedMillis", Duration.ofNanos(System.nanoTime() - startedAt).toMillis());
        return result;
    }

    public Map<String, Object> state() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("timeoutMillis", 500);
        result.put("actualAiCalls", actualAiCalls.get());
        result.put("activeAiCalls", activeAiCalls.get());
        result.put("finishedAiCalls", finishedAiCalls.get());
        result.put("interruptedAiCalls", interruptedAiCalls.get());
        result.put("timedOutRequests", timedOutRequests.get());
        return result;
    }

    @PreDestroy
    public void shutdown() {
        aiIoExecutor.shutdownNow();
    }
}
