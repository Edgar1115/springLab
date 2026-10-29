# 学习任务（Mission）

## 为什么学习

通过独立的 springLab 小实验，理解 AI-Meeting 项目中 AI 请求的容错保护机制，尤其是 Resilience4j CircuitBreaker 何时放行、如何统计异常与何时拒绝后续调用。

## 达成标准

能独立解释 `CircuitBreakerLabController` → `CircuitBreakerLabService` → `decorated.call()` 的执行链路；能预测 CLOSED、OPEN、HALF_OPEN 状态及 FAILED/BLOCKED 差别；能说明 `AtomicInteger` 的适用范围和并发限制。

## 当前范围

本轮只记录 CircuitBreaker 核心工作机制及 springLab 例子；不扩展阅读源码内部实现，不修改 AI-Meeting，不把实验参数误认为生产参数。

## 关联内容

- [学习记录](learning-records/0001-circuitbreaker-execution-and-state.md)
- [复习课程](lessons/0001-circuitbreaker-permission.html)
- [已有实操指南](../CircuitBreaker-实验.md)
