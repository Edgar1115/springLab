package com.edgar.springlab.circuitbreaker;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 独立于 AI-Meeting 的 CircuitBreaker 实验。
 * 不连接真实 AI，而是通过 fail 参数模拟调用成功或失败。
 */
@Service
public class CircuitBreakerLabService {

    // 熔断器实例
    private final CircuitBreaker circuitBreaker;

    // Micrometer 的监控指标注册中心。
    private final MeterRegistry meterRegistry;

    // Gauge 真正观察的对象：0=CLOSED，1=HALF_OPEN，2=OPEN/其他状态。
    private final AtomicInteger stateGauge;

    // 仅用于实验页面观察最近一次状态转换，例如 CLOSED_TO_OPEN。
    private final AtomicReference<String> lastStateTransition =
        new AtomicReference<>("NONE");

    // 统计模拟Ai代码实际执行了多少次
    // 支持多线程的线程安全的整数计数器
    private final AtomicInteger actualAiCalls = new AtomicInteger();

    public CircuitBreakerLabService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;

        // CircuitBreakerConfig的配置信息
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .slidingWindowType(
                CircuitBreakerConfig.SlidingWindowType.COUNT_BASED  // 按调用次数统计
            )
            .slidingWindowSize(4)   // closed状态下统计最近4次调用
            .minimumNumberOfCalls(4)    // 至少记录4次调用后才评估失败率
            .failureRateThreshold(50.0f)    // 失败率达到50%触发熔断
            .waitDurationInOpenState(Duration.ofSeconds(60))     // Open至少持续5秒才允许尝试恢复
            .permittedNumberOfCallsInHalfOpenState(2)       // HalfOpen允许2次试探调用
            .build();

        circuitBreaker = CircuitBreaker.of("spring-lab-ai", config);

        // 1. 先用 CircuitBreaker 的初始状态创建 Gauge 数据源。
        stateGauge = new AtomicInteger(
                // DEBUG:看真正的熔断器状态
            mapCircuitState(circuitBreaker.getState())
        );

        // 2. 把这个 AtomicInteger 注册成真正的 Micrometer Gauge。
        meterRegistry.gauge(
            "spring_lab_circuit_state",
            Tags.of("name", "spring-lab-ai"),
            stateGauge,
            AtomicInteger::get
        );

        // 3. 监听 CircuitBreaker 的状态转换。
        //    CLOSED -> OPEN、OPEN -> HALF_OPEN、HALF_OPEN -> CLOSED 等都会触发。
        circuitBreaker.getEventPublisher().onStateTransition(
            event -> {

                // DEBUG：查看发生了什么状态转换
                // 监听器被调用之前，已经发生了状态的转换
                CircuitBreaker.State newState =
                    event.getStateTransition().getToState();

                // 状态变化后更新 Gauge 观察的 AtomicInteger。
                // DEBUG：状态变化以后，监控值如何同步变化
                stateGauge.set(mapCircuitState(newState));

                // 保存最近一次转换，方便实验接口直接观察。
                lastStateTransition.set(
                    event.getStateTransition().name()
                );
            }
        );
    }

    public Map<String, Object> call(boolean fail) {
        String stateBefore = circuitBreaker.getState().name();  // 获取执行之前的熔断器状态

        // 对应 AI-Meeting 中被保护的 Callable。
        // 能被执行的情况是state为CLOSED
        Callable<String> originalAiCall = () -> {
            // DEBUG：CircuitBreaker有没有真正放业务代码进来执行
            int callNumber = actualAiCalls.incrementAndGet();

            if (fail) {
                throw new IOException(
                    "模拟 AI 调用失败，第 " + callNumber + " 次"
                );
            }

            return "模拟 AI 调用成功，第 " + callNumber + " 次";
        };

        // 装饰 Callable；这里还没有真正执行 originalAiCall。
        // 创建带有熔断器保护的任务
        Callable<String> decorated =
            CircuitBreaker.decorateCallable(
                circuitBreaker,
                originalAiCall
            );

        String outcome;
        String detail;

        try {
            // DEBUG：调用进入CircuitBreaker之前，它是什么状态
            detail = decorated.call();
            outcome = "SUCCESS";
        } catch (CallNotPermittedException ex) {
            // 熔断器阻止调用，originalAiCall 不会执行。
            // DEBUG: OPEN状态下请求为什么没有进入真正业务代码
            outcome = "BLOCKED";
            detail = "熔断器拒绝调用，未执行模拟 AI";
        } catch (Exception ex) {
            outcome = "FAILED";
            detail = ex.getMessage();
        }

        Map<String, Object> result = state();
        result.put("stateBefore", stateBefore);
        result.put("requestedFailure", fail);
        result.put("outcome", outcome);
        result.put("detail", detail);
        return result;
    }

    public Map<String, Object> state() {
        CircuitBreaker.Metrics metrics = circuitBreaker.getMetrics();   // 获取熔断器内部的统计信息
        Map<String, Object> result = new LinkedHashMap<>();

        result.put("state", circuitBreaker.getState().name());
        result.put("stateGauge", stateGauge.get());     // 给Micrometer Gauge用的整数值
        result.put("lastStateTransition", lastStateTransition.get());
        result.put("actualAiCalls", actualAiCalls.get());       // 看真正的业务代码到底执行了多少次
        result.put("bufferedCalls", metrics.getNumberOfBufferedCalls());
        result.put("successfulCalls", metrics.getNumberOfSuccessfulCalls());
        result.put("failedCalls", metrics.getNumberOfFailedCalls());
        result.put("rejectedCalls", metrics.getNumberOfNotPermittedCalls());
        result.put("failureRate", metrics.getFailureRate());
        return result;
    }

    /**
     * 从 MeterRegistry 中再把 Gauge 找出来读取一次，证明监控系统注册的值
     * 与 CircuitBreaker 当前状态保持同步。
     */
    public Map<String, Object> monitor() {
        Gauge gauge = meterRegistry.find("spring_lab_circuit_state")
            .tag("name", "spring-lab-ai")
            .gauge();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("circuitBreakerState", circuitBreaker.getState().name());
        result.put("stateGauge", stateGauge.get());
        result.put(
            "meterRegistryGauge",
            gauge == null ? null : gauge.value()
        );
        result.put("lastStateTransition", lastStateTransition.get());
        return result;
    }

    private int mapCircuitState(CircuitBreaker.State state) {
        if (state == CircuitBreaker.State.CLOSED) {
            return 0;
        }
        if (state == CircuitBreaker.State.HALF_OPEN) {
            return 1;
        }
        return 2;
    }

    public Map<String, Object> reset() {
        circuitBreaker.reset();     // 重置熔断器状态和指标
        stateGauge.set(mapCircuitState(circuitBreaker.getState()));
        lastStateTransition.set("RESET");
        actualAiCalls.set(0);       // 把实验中的执行次数归零
        return state();
    }
}
