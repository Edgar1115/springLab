# TimeLimiter 实验——调用方超时不等于后台任务终止

本实验与 AI-Meeting `AiCallGuardService.callWithTimeLimiter()` 结构一致：`TimeLimiter.decorateFutureSupplier(..., () -> CompletableFuture.supplyAsync(..., executor))`。不调用真实 AI 服务。

## 核心配置

```java
TimeLimiterConfig config = TimeLimiterConfig.custom()
    .timeoutDuration(Duration.ofMillis(500))
    .cancelRunningFuture(true)
    .build();
```

`timeoutDuration` 限制调用方的等待时间（500 毫秒）。`cancelRunningFuture(true)` 不等于保证已经开始的 `CompletableFuture.supplyAsync` 后台任务会被物理中断或远端 HTTP 请求会停止。实际取消能力取决于 Future 与底层 I/O 实现。

## 运行与请求

启动 `SpringLabApplication`（默认 localhost:8080），使用 IDEA HTTP Client / curl：

```bash
# 任务耗时 150ms，低于等待阈值 → SUCCESS
curl -s -X POST 'http://localhost:8080/lab/time-limiter/call?durationMillis=150'

# 任务耗时 1500ms，超过等待阈值 → 大约 500ms 后 TIMEOUT
curl -s -X POST 'http://localhost:8080/lab/time-limiter/call?durationMillis=1500'

# 紧接着查询：可能看到 activeAiCalls=1，说明 HTTP 早已超时但后台还在跑
curl -s http://localhost:8080/lab/time-limiter/state

# 等待超过 1.5 秒，确认后台任务是否继续执行完毕
sleep 2
curl -s http://localhost:8080/lab/time-limiter/state
```

关键字段：

- `outcome`：SUCCESS / TIMEOUT / FAILED
- `elapsedMillis`：调用方等待耗时
- `activeAiCalls`：当前仍在运行的模拟 AI 任务数
- `finishedAiCalls`：完成的后台模拟 AI 任务数
- `interruptedAiCalls`：被中断的任务数
- `timedOutRequests`：调用方收到 TIMEOUT 的次数

预期：短请求成功；长请求约 500ms 返回超时。对于这个 `CompletableFuture.supplyAsync` 方案，长请求的后台任务通常仍会运行到 1500ms 后完成。它说明 **HTTP 请求已超时返回** 和 **后台代码停止执行** 是两件不同的事。

对比 AI-Meeting：评分业务默认 TimeLimiter 为 20000ms；本实验采用 500ms 只为便于观察。因为生产中还有 Retry、Bulkhead、CircuitBreaker，不能以 TimeLimiter 的超时时间推断整个链路的总等待时长。
