# Bulkhead（舱壁隔离）并发实操

## 目标

在 Spring Boot 内，借助 Resilience4j 2.2.0 的 **SemaphoreBulkhead（信号量型舱壁）**模拟 AI 请求：**两个任务可同时执行；第三个请求不排队、立即拒绝**。它与 AI-Meeting 的 `AiCallGuardService.newBulkhead()` 同样使用 `BulkheadConfig`、`Bulkhead.decorateCallable`、`BulkheadFullException`。

实验只有模拟睡眠，不会访问真实 AI，也不会运行 AI-Meeting 的 Single-flight。

## 代码结构

- `src/main/java/com/edgar/springlab/bulkhead/BulkheadLabService.java`：创建 Bulkhead、装饰 Callable、模拟 AI 执行、统计并发与拒绝情况。
- `src/main/java/com/edgar/springlab/bulkhead/BulkheadLabController.java`：HTTP 接口。
- `pom.xml`：添加 `resilience4j-bulkhead:2.2.0`，与已有 `resilience4j-circuitbreaker:2.2.0` 并存。仅使用核心库，避免混用 Spring Boot 3 自动配置 Starter。

## 关键配置

```java
BulkheadConfig config = BulkheadConfig.custom()
    .maxConcurrentCalls(2)
    .maxWaitDuration(Duration.ZERO)
    .build();
Bulkhead bulkhead = Bulkhead.of("spring-lab-ai-bulkhead", config);
```

- `maxConcurrentCalls=2`：只有两个并发许可。
- `maxWaitDuration=Duration.ZERO`：许可均被占用时，**立即抛 `BulkheadFullException`**。
- `Bulkhead.decorateCallable(bulkhead, originalAiCall)`：创建包装 Callable，还不执行任务。
- `guarded.call()`：先申请许可，允许后才进入 `originalAiCall`；执行结束后归还许可，包括原始调用抛异常的情况。
- 这里用的是 Semaphore Bulkhead（并发许可），不是线程池型隔离；它**不会自动产生一个新的工作线程**。

## 运行

启动 `com.edgar.springlab.SpringLabApplication`，默认端口为 8080。

```bash
# 查看状态
curl -s http://localhost:8080/lab/bulkhead/state

# 重置计数（确保没有正在执行的实验调用）
curl -s -X POST http://localhost:8080/lab/bulkhead/reset
```

## 实验 1：单个请求可以成功执行

```bash
curl -s -X POST 'http://localhost:8080/lab/bulkhead/call?durationMillis=1000'
```

预期：约 1 秒后得到 `outcome=SUCCESS`，`actualAiCalls` 增加 1，结束后 `availableConcurrentCalls=2`。

## 实验 2：两个长请求占满许可，第三个立即被拒绝

在终端执行（后台发两个请求，让它们并行占用许可）：

```bash
curl -s -X POST 'http://localhost:8080/lab/bulkhead/call?durationMillis=4000' > /tmp/bulkhead-A.json &
pidA=$!
curl -s -X POST 'http://localhost:8080/lab/bulkhead/call?durationMillis=4000' > /tmp/bulkhead-B.json &
pidB=$!

sleep 0.3
curl -s http://localhost:8080/lab/bulkhead/state

# 应立即返回 REJECTED，不进入模拟 AI
curl -s -X POST 'http://localhost:8080/lab/bulkhead/call?durationMillis=100'

wait "$pidA" "$pidB"
cat /tmp/bulkhead-A.json
cat /tmp/bulkhead-B.json
curl -s http://localhost:8080/lab/bulkhead/state
```

当两项长请求仍在执行时：
- `availableConcurrentCalls=0`
- `activeAiCalls=2`
- 第三项响应 `outcome=REJECTED`、`rejectedRequests` 增加 1
- 第三项不会增加 `actualAiCalls`。
- 4 秒之后两项结束，`availableConcurrentCalls=2`。

如果第三项意外成功，通常是前两项尚未占满许可；先使用 `/state` 确认 `availableConcurrentCalls=0` 后再发第三项即可。

## 实验 3：限流后恢复（不需要等待固定冷却期）

结束两项请求后，立即执行：

```bash
curl -s -X POST 'http://localhost:8080/lab/bulkhead/call?durationMillis=200'
```

预期：成功，因为之前两个许可已经释放。Bulkhead **没有 CircuitBreaker 的 OPEN/HALF_OPEN 状态，也不按失败率拒绝**。

## 与 AI-Meeting 对应

`AiCallGuardService.newBulkhead(stage)` 使用 `configuration.resolveStagePolicy(stage)` 读取不同业务阶段允许的最大并发数，并设置 `maxWaitDuration(Duration.ZERO)`。当前评分阶段默认最大并发数为 30，抽题阶段为 8（实际值受配置覆盖影响），这些都是**每个 AI Guard 实例 / JVM 的并发限制**，不是 Redis 全局配额。生产环境中某些请求可被 Single-flight 合并，不一定进入 Bulkhead。

在 AI-Meeting 中，Bulkhead 位于 CircuitBreaker 之内、Retry 和 TimeLimiter 之外。因此它限制的是进入它保护区间的并发调用数量，**不能直接等同于所有后台 AI I/O 线程一定只有这么多**，特别是 TimeLimiter 超时后后台任务是否真正停止还需独立检查。
