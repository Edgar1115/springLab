# Future 基础实验——`submit()` 异步执行，`get()` 等待结果

本实验对应 `src/main/java/com/edgar/springlab/future/FutureGetLab.java`，目的是在学习 `CompletableFuture` 之前，看清最基础的 `ExecutorService + Future` 机制。

## 怎么运行

在 IDEA 中找到 `FutureGetLab`，直接运行它的 `main()`。**不需要启动 `SpringLabApplication`，不需要调用 HTTP 接口。**

也可以在 SpringLab 项目根目录执行：

```bash
./mvnw -q -DskipTests compile
java -cp target/classes com.edgar.springlab.future.FutureGetLab
```

## 需要观察的三个事实

1. **`executor.submit(...)` 不等待任务完成**：工作线程 `ai-worker` 执行 lambda，主线程 `main` 很快执行后续语句。线程调度具有不确定性，两条线程的日志输出顺序不必完全固定。
2. **`Future<String>` 是任务结果的句柄**：`future.isDone()` 表示任务是否已经结束；它本身不等待任务。
3. **`future.get()` 可能阻塞调用它的线程**：默认后台任务休眠约 3 秒，主线程先做约 1 秒其他工作，因此随后 `get()` 通常还要等约 2 秒（时间不是严格保证）。

典型日志类似：

```text
    0 ms | main      | 准备通过 submit() 提交模拟 AI 请求
   10 ms | ai-worker | 模拟 AI 请求开始：准备耗时 3 秒
   11 ms | main      | submit() 已返回；future.isDone() = false
   11 ms | main      | 主线程继续处理其他工作（约 1 秒）
 1012 ms | main      | 主线程其他工作完成；future.isDone() = false
 1012 ms | main      | 即将调用 future.get()
 3012 ms | ai-worker | 模拟 AI 请求结束：返回结果
 3013 ms | main      | get() 返回，等待约 2000 ms；结果：AI回答
```

## 建议的 Debug 断点

在 `FutureGetLab` 中依次打断点：

1. **`Future<String> future = executor.submit(...)`**：观察当前线程为 `main`；Step Over 后，`future` 是已提交任务的句柄，但任务可能仍在运行。
2. **lambda 内的 `Thread.sleep(3000)`**：观察当前线程名为 `ai-worker`，不是 `main`。
3. **`submit()` 返回之后的 `future.isDone()`**：查看 `future` 的状态；通常为 `false`，但不能把它理解为永久固定。
4. **`String result = future.get()`**：观察 `main`；默认在子线程还没完成时，Step Over 会等到结果就绪。
5. **lambda 内的 `return "AI回答"`**：任务返回值将被线程池对应的 `Future` 保存。
6. **`get()` 下一行**：观察 `result` 为 `"AI回答"`，`getWaitMillis` 是此次 `get()` 实际等待时间。

**Debug 提醒：**如果 IDEA 设置了 `Suspend: All`，在工作线程断点处可能把主线程也暂停，从而改变观察到的等待过程。要看并发执行，建议把这类断点设为 `Suspend: Thread`。

## 两个小实验

- 把主线程的 `Thread.sleep(1000)` 改为 `Thread.sleep(3500)`：主线程做完其他工作时，后台任务通常已完成，此时 `future.isDone()` 通常为 `true`，`get()` 基本不需要等待。
- 把主线程的 `Thread.sleep(1000)` 删除：几乎立刻调用 `get()`，就会在 `get()` 处等待接近 3 秒。

注意：`get()` 不负责启动任务；任务在 `submit()` 之后便有机会执行。若任务抛异常，`get()` 会抛出 `ExecutionException`；若等待的线程被中断，则可能抛出 `InterruptedException`。本例通过 `main throws` 让异常直接显现，`finally` 确保关闭线程池。`shutdown()` 是拒绝新任务并等待已提交任务自然完成的关闭请求，不是立刻强行停止执行。

后续对比 `CompletableFuture.supplyAsync()` 时可以重点看：两者都会提交异步计算；`Future.get()` 是等待方式之一，而 `CompletableFuture` 另外支持 `thenApply()`、`thenCompose()` 等结果就绪后的组合操作。
