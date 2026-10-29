# 教学备忘（Notes）

- 学习者以中文逐步理解 Java / Spring / Redis / AI-Meeting；倾向先看源码、再用 springLab 完成小实验。
- 已理解单线程与并发访问的基本差异，学习了 `AtomicInteger`、`AtomicBoolean`、`Callable` 与 Lombok `@RequiredArgsConstructor`。
- 本轮曾把“允许执行”与“执行成功”混淆；曾认为 HALF_OPEN 一次成功即可 CLOSE、且试探许可可重复利用，已在对话中纠正。
- 练习时使用精确时序题：第 4 次 FAILED 触发 OPEN，第 5 次 BLOCKED；两个 HALF_OPEN 试探许可已发放，A 结束 B 尚未结束时 C 仍 BLOCKED。
- 后续宜采用短步骤、小型请求实验及延迟回忆，不直接跳到复杂的 Resilience4j 内部源码。
