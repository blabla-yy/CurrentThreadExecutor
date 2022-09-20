package com.blabla.executor;

import com.blabla.executor.exception.NotWorkerThreadException;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Slf4j
public class CurrentThreadExecutor implements Executor {
    public enum Status {
        READY, // 就绪
        RUNNING, // 运行中
        COMPLETED, // 正常结束
        TIMEOUT, // 超时
        TERMINATED; // 被终止，shutDown

        /**
         * @return 是否已经完成
         */
        public boolean hasClosed() {
            return this == COMPLETED || this == TERMINATED || this == TIMEOUT;
        }
    }

    /**
     * 任务队列
     */
    protected final LinkedBlockingQueue<Runnable> tasks;
    /**
     * 轮询超时时间，毫秒
     * 小于 0 则会一直阻塞直到有新的任务
     */
    protected final long pollTimeout;
    /**
     * 工作线程，即创建对象时的线程
     */
    private final Thread workerThread;
    // 超时时间毫秒
    private long timeout;
    // start时间，毫秒
    private long startTime;
    protected volatile Status status;
    protected CompletableFuture<?> finalResult;

    public CurrentThreadExecutor(int pollTimeout, TimeUnit timeUnit) {
        this.pollTimeout = timeUnit.toMillis(pollTimeout);
        this.tasks = new LinkedBlockingQueue<>();
        this.workerThread = Thread.currentThread();
        this.status = Status.READY;
    }

    public CurrentThreadExecutor() {
        this.pollTimeout = TimeUnit.SECONDS.toMillis(1);
        this.tasks = new LinkedBlockingQueue<>();
        this.workerThread = Thread.currentThread();
        this.status = Status.READY;
    }

    /**
     * ThreadSafe*
     *
     * @param command the runnable task
     */
    @Override
    public void execute(Runnable command) {
        if (command == null) {
            throw new NullPointerException();
        }
        if (this.status.hasClosed()) {
            log.warn("CurrentThreadExecutor is closed");
        }
        tasks.add(command);
    }

    /**
     * 当前线程是否为工作线程
     */
    public boolean isWorkerThread() {
        return Thread.currentThread() == this.workerThread;
    }

    public Status getStatus() {
        return status;
    }

    public <T> T start(Supplier<CompletableFuture<T>> finalResult, long timeout, TimeUnit timeUnit) throws InterruptedException {
        /*
          先执行start函数，然后再执行任务。
          使用CompletableFuture而不是直接this.execute，是为了能够方便得到future，本质一样的。
         */
        CompletableFuture<T> future = CompletableFuture.runAsync(() -> {
                }, this)
                .thenCompose(ignored -> finalResult.get());
        return this.start(future, timeout, timeUnit);
    }

    /**
     * 执行任务，直到finalResult完成。
     * 1. 如果任务与finalResult无关，则可能会不执行。
     * 2. 结束时不会清空剩余的无关任务。可以手动清理/等待GC
     * 3. start时，会执行在次函数之前提交的任务。（不会清理tasks）
     * 4. 结果使用join函数，可能会抛出CompletableFuture异常（如：取消等），但此处不捕获，继续向上抛出。
     *
     * @param finalResult 退出执行的条件：此future已经完成
     * @param timeout     超时时间。<= 0时，为没有超时限制。注意此超时非准确超时时间，且不会中断正在执行的函数。
     * @param timeUnit    超时时间单位
     * @param <T>         future泛型
     * @return finalResult的结果，如果没有完成（执行器超时/中断）返回null。
     */
    public <T> T start(CompletableFuture<T> finalResult, long timeout, TimeUnit timeUnit) throws InterruptedException {
        if (timeout > 0) {
            timeout = timeUnit.toMillis(timeout);
        }

        if (!isWorkerThread()) {
            throw new NotWorkerThreadException();
        }
        this.status = Status.RUNNING;
        this.finalResult = finalResult;
        this.timeout = timeout;
        this.startTime = System.currentTimeMillis();

        while (!this.checkStatus()) {
            process();
        }
        if (futureHasDone(finalResult)) {
            return finalResult.join();
        }
        log.warn("future not finished");
        return null;
    }

    public int getWaitingJobSize() {
        return tasks.size();
    }

    public void clearTasks() {
        this.tasks.clear();
    }

    /**
     * 开始循环处理事件
     * 退出时，即最终事件处理完毕。但提交的其他事件会被丢弃
     */
    public <T> T start(CompletableFuture<T> finalResult) throws InterruptedException {
        return this.start(finalResult, -1, TimeUnit.MILLISECONDS);
    }

    /**
     * 终止事件处理，后续任务将不再执行*
     * 但finalResult也可能永远不会执行完成，需要自行判断。
     *
     * @return 剩余的任务数量（有并发带来的数量问题）*
     */
    public int shutDown() {
        this.status = Status.TERMINATED;
        return tasks.size();
    }

    /**
     * 最终的Future已经完成，或者提前终止，超时*
     *
     * @return 是否结束处理
     */
    protected boolean checkStatus() {
        if (this.status.hasClosed()) {
            return true;
        }
        if (finalResult == null || futureHasDone(finalResult)) {
            this.status = Status.COMPLETED;
        } else if (this.timeout > 0 && this.startTime > 0
                && (System.currentTimeMillis() - this.startTime > timeout)) {
            this.status = Status.TIMEOUT;
        }
        return this.status.hasClosed();
    }

    public static boolean futureHasDone(CompletableFuture<?> future) {
        return future.isDone() || future.isCancelled() || future.isCompletedExceptionally();
    }

    protected void process() throws InterruptedException {
        // 不配置超时会空转一段时间。
        // 也可以尝试使用take函数，永久阻塞等待事件到来，或是interrupt事件。
        Runnable task;
        if (pollTimeout < 0) {
            task = tasks.take();
        } else {
            task = tasks.poll(pollTimeout, TimeUnit.MILLISECONDS);
        }

        if (task != null) {
            task.run();
        }
    }
}
