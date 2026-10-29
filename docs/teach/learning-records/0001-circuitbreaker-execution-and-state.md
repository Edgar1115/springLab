# 0001 · CircuitBreaker 包装执行、状态机与并发计数

- **学习日期**：2026-10-21
- **主题**：SpringLab 的 CircuitBreaker 实操；联系 AI-Meeting 中的受保护 AI 调用
- **对应代码**：
  - [CircuitBreakerLabController.java](../../../src/main/java/com/edgar/springlab/circuitbreaker/CircuitBreakerLabController.java)
  - [CircuitBreakerLabService.java](../../../src/main/java/com/edgar/springlab/circuitbreaker/CircuitBreakerLabService.java)
  - [pom.xml](../../../pom.xml)
- **已有实验步骤**：[CircuitBreaker-实验.md](../../CircuitBreaker-实验.md)
- **速查表**：[CircuitBreaker 执行与状态速查](../reference/0001-circuitbreaker-reference.html)
- **复习小课**：[执行许可与 HALF_OPEN 试探](../lessons/0001-circuitbreaker-permission.html)

## 学习目的与背景

在阅读 AI-Meeting 的 AI 调用保护逻辑时遇到 CircuitBreaker；先在独立 springLab 里验证它如何包装 `Callable`、统计失败、拒绝请求以及恢复探测。关注的是**执行许可与实际执行结果的区别**，不是重新造一个熔断器。

## 1. 两个类分别做什么

| 类 | 职责 | 代码入口 |
| --- | --- | --- |
| `CircuitBreakerLabController` | 通过 REST 接口接收实验请求，并委托 Service | `POST /lab/circuit-breaker/call?fail=true/false`、`GET /state`、`POST /reset` |
| `CircuitBreakerLabService` | 创建一个 CircuitBreaker，构造模拟 AI `Callable`，执行被保护的任务，记录与返回指标 | `call(boolean fail)`、`state()`、`reset()` |

Controller 的 `@RestController` 负责 REST 响应，`@RequestMapping("/lab/circuit-breaker")` 提供公共 URL 前缀；`@RequiredArgsConstructor` 为 `private final CircuitBreakerLabService labService` 生成构造函数，Spring 注入 `@Service` Bean。Controller **不实现熔断规则**。

完整调用链：
```text
HTTP POST /lab/circuit-breaker/call?fail=true
  → Controller.call(fail)
  → Service.call(fail)
  → originalAiCall = () -> { 计数；成功返回 / 抛 IOException }
  → decorated = CircuitBreaker.decorateCallable(circuitBreaker, originalAiCall)
  → decorated.call()
      → 申请许可
      → 获准才执行 originalAiCall.call()
      → 按返回值/异常报告结果并更新熔断状态
  → Service 捕获异常、构造 outcome 和 metrics
  → Controller 返回 JSON
```

## 2. 包装和执行是两个时刻

```java
Callable<String> originalAiCall = () -> {
    int n = actualAiCalls.incrementAndGet();
    if (fail) throw new IOException("模拟 AI 调用失败，第 " + n + " 次");
    return "模拟 AI 调用成功，第 " + n + " 次";
};

Callable<String> decorated =
    CircuitBreaker.decorateCallable(circuitBreaker, originalAiCall);

// 到这里 Lambda 还没运行，也没有提前固定 CircuitBreaker 状态
detail = decorated.call(); // 此处才申请许可并尝试运行
```

- `decorateCallable(...)` 创建带保护逻辑的 `Callable`，**不触发实际 AI 调用**。
- `decorated.call()` 按**调用时的状态**申请许可。即使包装时为 CLOSED，执行时已变成 OPEN，也会拒绝（除非满足等待条件并获得 HALF_OPEN 试探许可）。
- 允许执行 ≠ 执行成功：放行后 `originalAiCall` 仍可能抛 `IOException`。
- 允许执行后的返回/抛异常由装饰层报告给熔断器（概念上对应 `onSuccess`/`onError`）；因此 Service 外层后来 `catch (Exception)` **不会抹掉熔断统计**。
- 若原任务仅返回普通字符串 `"失败"` 而不抛异常，在此默认配置下通常被视为成功；异常分类/结果判定是可以单独配置的。

## 3. SpringLab 的熔断参数

来自 `CircuitBreakerLabService` 构造方法：

| 配置 | 数值 | 效果 |
| --- | --- | --- |
| `slidingWindowType` | `COUNT_BASED` | CLOSED 时按完成的调用计数 |
| `slidingWindowSize` | `4` | 保留最近 4 次调用结果 |
| `minimumNumberOfCalls` | `4` | 至少 4 次才能评估失败率 |
| `failureRateThreshold` | `50%` | 达到阈值触发 OPEN |
| `waitDurationInOpenState` | 5 秒 | OPEN 等待时间 |
| `permittedNumberOfCallsInHalfOpenState` | 2 | HALF_OPEN 本轮最多发放两个试探许可 |

例：四次为 失败、成功、失败、成功，第四次结束失败率为 50%，**即使第四次成功，也会触发 OPEN**。未达到至少 4 次时，即便连续失败也不会按失败率触发熔断。

## 4. CLOSED / OPEN / HALF_OPEN 的准确含义

| 状态 | 执行许可 | 进入下一状态的条件 |
| --- | --- | --- |
| `CLOSED` | 正常允许调用 | 样本数足够且失败率达到阈值 → OPEN |
| `OPEN` | 拒绝普通调用（`CallNotPermittedException`） | 本实验默认是等够 5 秒、在后续请求触发检查时转 HALF_OPEN；并非定时任务自动转换 |
| `HALF_OPEN` | 只允许**有限个**试探请求 | 收集本轮试探结果后：失败率达阈值 → OPEN，否则 → CLOSED |

服务层的结果分类：
- `SUCCESS`：任务已运行且正常返回；
- `FAILED`：任务已运行但抛异常；
- `BLOCKED`：未获执行许可，原任务没有运行。

`outcome` 是业务字段，不是 HTTP 状态码；此实验会捕获异常并返回 `Map`，因此 FAILED/BLOCKED 也通常是 HTTP 200 响应。

**关键时序题**：第 4 次任务失败后让 CLOSED → OPEN，则第 4 次是 **FAILED**（先执行，后统计并熔断），立刻到来的第 5 次才是 **BLOCKED**。

## 5. HALF_OPEN 的许可不是可周转的并发槽位

这里设置 `permittedNumberOfCallsInHalfOpenState(2)`。本轮 HALF_OPEN 最多两个**实际试探调用许可**。

- A 获得第 1 个许可并成功，熔断器通常仍为 HALF_OPEN：不能凭 1 次成功就直接恢复。
- B 已获得第 2 个许可且尚未结束；此时 C 到来仍是 BLOCKED。
- 即使 A 已成功，它用过的许可也**不会自动返还给 C**。它不是“允许两个并发请求，完成一个马上空出一个”的并发控制器。
- 两次试探均结束后：两成功→恢复 CLOSED；成功+失败→失败率 50%，再次 OPEN；两失败→再次 OPEN。
- 特殊情况：若任务在拿到许可后**根本没有执行**而取消，底层 API 有 `releasePermission()` 语义；不要与正常执行成功后的统计混淆。

## 6. AtomicInteger 是什么、为什么在这里使用

`private final AtomicInteger actualAiCalls = new AtomicInteger();` 的初值为 0。它是原子整数操作工具，`incrementAndGet()` 可保证多个线程并发递增时不丢失更新。

普通 `int count++` 涉及“读取 → 计算 → 写回”，并发时可能两个线程都读取 5、都写回 6；`AtomicInteger.incrementAndGet()` 则使递增作为原子操作生效，常见实现使用 CAS。

| API | 作用 |
| --- | --- |
| `get()` | 读取当前值 |
| `set(0)` | 重置为 0 |
| `incrementAndGet()` | 递增并返回新值 |
| `getAndIncrement()` | 返回旧值并递增 |
| `compareAndSet(expected, update)` | 当前值等于预期值时原子更新 |

**边界**：单个原子操作安全，不等于 `if (get()<10) incrementAndGet()` 这种多步组合整体原子；并且 AtomicInteger 只在本 JVM 内共享，跨 Spring Boot 实例不会共享计数。Spring 单例 Service 接受并发请求，Resilience4j CircuitBreaker 自身设计为线程安全，不意味着所有自定义业务状态自动线程安全。

`actualAiCalls` 只在原任务真正进入时递增；被 BLOCKED 的请求不增加。不要将它与滑动窗口的 `bufferedCalls` 混同。

## 7. 从交流中纠正的三个误区

1. **“只有 CLOSED/HALF_OPEN 才能成功”** → 更准确说这些状态可能提供“执行许可”，不是保证业务成功；HALF_OPEN 还受许可数量限制。
2. **“HALF_OPEN 第一个请求成功就回 CLOSED”** → 这份配置需本轮两个试探调用的结果完成后评估。
3. **“A 成功释放额度，C 能接着试探”** → 正常完成的试探调用占用本轮总配额；B 未完成时 C 仍被拒绝。

已正确回答的推演：第 4 次触发 OPEN 的请求为 FAILED；包装后到调用前状态从 CLOSED 变为 OPEN，执行时会按最新状态拒绝；两个试探许可已发放时第三个请求不能执行。

## 8. 可重复验证

先启动应用（默认 8080）：

```bash
./mvnw spring-boot:run
curl -X POST http://localhost:8080/lab/circuit-breaker/reset
curl -X POST 'http://localhost:8080/lab/circuit-breaker/call?fail=true'
curl -X POST 'http://localhost:8080/lab/circuit-breaker/call?fail=true'
curl -X POST 'http://localhost:8080/lab/circuit-breaker/call?fail=true'
curl -X POST 'http://localhost:8080/lab/circuit-breaker/call?fail=true'
# 在 OPEN 5 秒内立即追加请求，才容易观察 BLOCKED：
curl -X POST 'http://localhost:8080/lab/circuit-breaker/call?fail=false'
curl http://localhost:8080/lab/circuit-breaker/state
```

然后等待 5 秒以上，连续发送两次 `fail=false` 观察 HALF_OPEN → CLOSED；建议每次实验前 `reset`，避免状态互相影响。计时要注意实际请求间隔。

指标要看 `stateBefore`（执行前）、`state`（执行后）、`actualAiCalls`（实际执行累计数）、`bufferedCalls`（当前统计窗口）、`rejectedCalls` 和 `failureRate`（样本不足可能为 -1）。

**IDE 排错记录**：最初 VS Code 的 `io.github.resilience4j` import 标红，但仓库 `pom.xml` 已声明 `resilience4j-circuitbreaker:2.2.0`，本地 JAR 存在，`mvn -q -DskipTests compile` 退出码为 0；属于 IDE 依赖模型/Java Language Server 未刷新的可能性较高。建议 `Java: Clean Java Language Server Workspace`，不需要修改 import 或重复加依赖。

## 9. 下次复习（先凭记忆回答）

1. `decorateCallable()` 和 `decorated.call()` 谁真正执行 Lambda？
2. 如果第 4 次运行失败导致 OPEN，第 4 次与第 5 次分别是什么 outcome？
3. HALF_OPEN 已发放 2 个许可，A 成功、B 还在运行，C 能否执行？
4. `AtomicInteger` 为什么比 `int++` 更适合并发计数？为什么它不能跨 JVM 共享？
5. `fail=false` 何时仍可能得到 BLOCKED？
6. 仅返回字符串“失败”，CircuitBreaker 是否一定将它算成失败？为什么？

## 10. 可信资料

- [Resilience4j 官方 CircuitBreaker 指南](https://resilience4j.readme.io/docs/circuitbreaker)
- [Resilience4j 官方 API/用法示例](https://resilience4j.readme.io/docs/examples)
- [Java 17 AtomicInteger API](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/AtomicInteger.html)
- 本仓库 `CircuitBreakerLabController.java`、`CircuitBreakerLabService.java`、`pom.xml` 是这份笔记对实验参数和流程的直接依据。

> 下一轮学习建议：在 springLab 上验证 HALF_OPEN 并发两许可与 `rejectedCalls` 计数，再回到 AI-Meeting 中 `AiCallGuardService` 的实际装饰和调用顺序。此建议不是已完成项。
