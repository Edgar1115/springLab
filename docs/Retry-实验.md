# Retry 实验——同一个 Callable 失败后是否重试？

本实验与 AI-Meeting 的 `AiCallGuardService.newRetry()` 对齐：使用 `RetryConfig`、`Retry.decorateCallable`；所有调用均为模拟 AI，不发真实网络请求。

## 核心配置

```java
RetryConfig config = RetryConfig.custom()
    .maxAttempts(3)
    .waitDuration(Duration.ofMillis(200))
    .retryOnException(ex -> ex instanceof IOException)
    .ignoreExceptions(IllegalArgumentException.class)
    .build();
```

**maxAttempts 包含首次调用**；本实验最多 3 次、间隔 200ms，且只对 IOException 重试。AI-Meeting 评分阶段默认 `retryCount=1`，会转换为 `maxAttempts=2`；本实验故意设置 3 次便于看清过程。

## 运行与请求

启动 `SpringLabApplication`（默认 localhost:8080），使用 IDEA HTTP Client / curl：

```bash
# 前两次 IOException，第三次成功
curl -s -X POST 'http://localhost:8080/lab/retry/call?scenario=recover'

# 三次都 IOException，最终失败，不会无限尝试
curl -s -X POST 'http://localhost:8080/lab/retry/call?scenario=always-fail'

# IllegalArgumentException 不在允许重试的异常范围，仅执行一次
curl -s -X POST 'http://localhost:8080/lab/retry/call?scenario=validation'

# 第一次就成功，不发生额外重试
curl -s -X POST 'http://localhost:8080/lab/retry/call?scenario=success'

# 统计所有真正进入原始模拟 Callable 的次数
curl -s http://localhost:8080/lab/retry/state
```

关注返回字段 `attemptsThisRequest`（本次真实调用次数）、`outcome` 和 `elapsedMillis`。

预期：

| 场景 | outcome | attemptsThisRequest | 原因 |
| --- | --- | --- | --- |
| recover | SUCCESS | 3 | 前 2 次 IOException，重试后成功 |
| always-fail | FAILED | 3 | 达到 maxAttempts 上限 |
| validation | FAILED | 1 | IllegalArgumentException 不重试 |
| success | SUCCESS | 1 | 第一次成功就结束 |

**Retry 不会自己创建新线程**；直接使用 `decorateCallable` 的例子在当前调用线程里执行。AI-Meeting 中真正的 AI I/O 则由内层 `TimeLimiter` 的 `CompletableFuture.supplyAsync` 放到专用线程池。

实验不包含 CircuitBreaker 或 Bulkhead；生产代码的外层装饰器决定熔断和并发许可。TimeLimiter 被放在 Retry 内部时，每次重试可以分别触发一次超时判断，整个 Retry 的耗时可能超过一次超时阈值。
