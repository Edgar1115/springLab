package com.edgar.springlab.future;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Future 基础实验：submit() 提交异步任务，get() 在结果未就绪时阻塞调用线程。
 *
 * 直接运行本类的 main()，不需要启动 Spring Boot。
 */
public class FutureGetLab {

    public static void main(String[] args) throws InterruptedException, ExecutionException {

        // 高精度的时间计数值
        long startNanos = System.nanoTime();

        // 只有一个工作线程，与运行 main() 的主线程不是同一个线程。
        ExecutorService executor = Executors.newSingleThreadExecutor(
            task -> new Thread(task, "ai-worker")
        );

        try {
            log(startNanos, "准备通过 submit() 提交模拟 AI 请求");

            // submit() 返回 Future；任务体交给线程池执行，不会在主线程中等待 3 秒。
            Future<String> future = executor.submit(() -> {
                log(startNanos, "模拟 AI 请求开始：准备耗时 3 秒");
                Thread.sleep(3000);
                log(startNanos, "模拟 AI 请求结束：返回结果");
                return "AI回答";
            });

            log(startNanos, "submit() 已返回；future.isDone() = " + future.isDone());

            // 主线程可以在后台任务运行期间继续执行自己的工作。
            log(startNanos, "主线程继续处理其他工作（约 1 秒）");
            Thread.sleep(1000);
            log(startNanos, "主线程其他工作完成；future.isDone() = " + future.isDone());

            // 如果后台任务尚未完成，这一行会阻塞当前主线程，而不是阻塞工作线程。
            log(startNanos, "即将调用 future.get()");
            long beforeGetNanos = System.nanoTime();
            String result = future.get();
            long getWaitMillis = (System.nanoTime() - beforeGetNanos) / 1_000_000;

            log(startNanos, "get() 返回，等待约 " + getWaitMillis + " ms；结果：" + result);
        } finally {
            // 不再接受新任务；已提交的任务会继续运行直至完成。
            executor.shutdown();
        }
    }

    private static void log(long startNanos, String message) {
        long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;
        System.out.printf(
            "%5d ms | %-9s | %s%n",
            elapsedMillis,
            Thread.currentThread().getName(),
            message
        );
    }
}
