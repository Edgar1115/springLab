package com.edgar.springlab.retry;

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Retry 独立实验：演示按异常类型重试，以及 maxAttempts 包含首次调用。
 */
@Service
public class RetryLabService {

    private final Retry retry;
    private final AtomicInteger actualAiCalls = new AtomicInteger();

    public RetryLabService() {
        RetryConfig config = RetryConfig.custom()
            .maxAttempts(3) // 最多尝试 3 次（包含第 1 次）
            .waitDuration(Duration.ofMillis(200))       // 两次尝试之间等待200ms
            .retryOnException(ex -> ex instanceof IOException)      // 指定哪些异常需要触发重试
            .ignoreExceptions(IllegalArgumentException.class)                 // 指定哪些异常不需要重试
            .build();

        retry = Retry.of("spring-lab-retry", config);
    }

    public Map<String, Object> call(String scenario) {
        AtomicInteger attemptsThisRequest = new AtomicInteger();

        Callable<String> originalAiCall = () -> {
            int attempt = attemptsThisRequest.incrementAndGet();
            actualAiCalls.incrementAndGet();

            // 故障注入：由 HTTP 参数决定模拟哪类异常。
            return switch (scenario) {
                case "recover" -> {
                    if (attempt <= 2) {
                        throw new IOException("前两次模拟网络故障，attempt=" + attempt);
                    }
                    yield "第 " + attempt + " 次调用成功";
                }
                case "always-fail" -> throw new IOException(
                    "每次都发生模拟网络故障，attempt=" + attempt
                );
                case "validation" -> throw new IllegalArgumentException(
                    "模拟参数错误，不应该重试"
                );
                case "success" -> "第一次调用就成功";
                default -> throw new IllegalArgumentException(
                    "未知场景: " + scenario
                );
            };
        };

        // 这里只生成受 Retry 保护的 Callable，尚未调用 AI。
        Callable<String> decorated = Retry.decorateCallable(
            retry,
            originalAiCall
        );

        long startedAt = System.nanoTime();
        String outcome;
        String detail;

        try {
            detail = decorated.call(); // 真正开始执行；符合条件才自动重试
            outcome = "SUCCESS";
        } catch (Exception ex) {
            outcome = "FAILED";
            detail = ex.getClass().getSimpleName() + ": " + ex.getMessage();
        }

        Map<String, Object> result = state();
        result.put("scenario", scenario);
        result.put("outcome", outcome);
        result.put("detail", detail);
        result.put("attemptsThisRequest", attemptsThisRequest.get());
        result.put("elapsedMillis", Duration.ofNanos(System.nanoTime() - startedAt).toMillis());
        return result;
    }

    public Map<String, Object> state() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("maxAttempts", 3);
        result.put("waitBetweenAttemptsMillis", 200);
        result.put("actualAiCallsTotal", actualAiCalls.get());
        return result;
    }
}
