# CircuitBreaker（熔断器）实操

本实验使用 Spring Boot HTTP 接口和 Resilience4j 2.2.0 **核心库**，不依赖 Redis、真实 AI 服务或 AI-Meeting。使用 `Callable<String>` 和 `CircuitBreaker.decorateCallable(...)`，呼应 AI-Meeting 的 `AiCallGuardService.guardedExecute()`。

## 1. 运行

在 springLab 项目根目录启动 `SpringLabApplication`，或者运行：

```bash
./mvnw spring-boot:run
```

默认监听 8080。如果该端口占用，可传入 `-Dspring-boot.run.arguments=--server.port=18080` 并把下面 URL 端口也改成 18080。

## 2. 实验参数

| 配置 | 实验值 | 含义 |
| --- | --- | --- |
| slidingWindowType | COUNT_BASED | 根据最近完成的调用次数统计，而不是按秒 |
| slidingWindowSize | 4 | 统计最近 4 次调用 |
| minimumNumberOfCalls | 4 | 至少观察 4 次已完成调用才计算是否熔断 |
| failureRateThreshold | 50% | 失败率达到 50% 则触发熔断 |
| waitDurationInOpenState | 5 秒 | OPEN 后等待 5 秒，下一次调用才有机会进入 HALF_OPEN |
| permittedNumberOfCallsInHalfOpenState | 2 | HALF_OPEN 允许两次试探调用 |

`CLOSED`=允许调用；`OPEN`=拒绝调用；`HALF_OPEN`=少量试探。

注意：默认不是等待 5 秒后由独立定时任务直接改状态；下一次调用可能触发 `OPEN → HALF_OPEN`。需要在 OPEN **5 秒内**发起额外请求才能观察 BLOCKED。

## 3. HTTP 接口（可以用 IDEA HTTP Client 或 curl）

```bash
# 重置：重新开始每组实验（清零熔断指标和模拟调用计数）
curl -s -X POST http://localhost:8080/lab/circuit-breaker/reset

# 模拟请求 AI 失败；该请求会被 CircuitBreaker 计入失败
curl -s -X POST 'http://localhost:8080/lab/circuit-breaker/call?fail=true'

# 模拟请求 AI 成功；在 OPEN 状态下即使 fail=false 也会被拒绝
curl -s -X POST 'http://localhost:8080/lab/circuit-breaker/call?fail=false'

# 查看状态，不会触发新的 AI 调用
curl -s http://localhost:8080/lab/circuit-breaker/state
```

### 实验 A：失败触发熔断

1. reset，观察 `state=CLOSED, actualAiCalls=0`。
2. 尽快连续调用 `fail=true` 4 次，观察第 4 次后 `state=OPEN`。
3. **在 5 秒内**立即再调用一次 `fail=false`，应得到 `outcome=BLOCKED`；`actualAiCalls` 不增加。
4. 等 5 秒以上，调用 `fail=false` 一次，观察 `state=HALF_OPEN`。
5. 再调用一次 `fail=false`，观察 `state=CLOSED`。

### 实验 B：计算失败率，不是连续失败次数

1. reset 后按照 `fail=false, fail=true, fail=false, fail=true` 顺序依次调用。
2. 第 4 次后，4 次调用中 2 次失败，失败率 = 50%；达到阈值，观察 `state=OPEN`。
3. 如果 reset 后只调用 1 次失败，因为 `minimumNumberOfCalls=4`，仍处于 `CLOSED`。

## 4. 重点看什么

- `stateBefore` 和 `state`：一次请求前后的熔断状态。
- `outcome`：`SUCCESS`、`FAILED` 或 `BLOCKED`。
- `actualAiCalls`：进入原始 Callable 的次数。**BLOCKED 时不增加**，证明真正保护了下游调用。
- `bufferedCalls`/`failureRate`：滑动窗口内的统计；未达到最小样本数时失败率可能是 `-1`，不是 0%。

比较 AI-Meeting：本实验仅使用 CircuitBreaker，不含 Bulkhead、Retry、TimeLimiter，也不负责分布式 Single-flight。AI-Meeting 原工程的阈值及窗口由 `InterviewAiGuardConfiguration` 决定；这份实验的 4 次、50%、5 秒是为了方便观察，不能视为其生产参数。
