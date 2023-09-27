package com.blabla.executor;

import com.blabla.executor.exception.NotWorkerThreadException;
import com.blabla.executor.exception.UnexpectedStatusException;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Slf4j
public class CurrentThreadExecutor implements Executor {
    protected final LinkedBlockingQueue<Runnable> tasks;
    /**
     * ensure all task will run on this thread
     */
    private final Thread workerThread;

    /**
     * nullable
     * similar to next-tick in node.js
     * it will be executed every time the queue is empty
     */
    private final Runnable nextTick;
    private long timeout;
    /**
     * milliseconds
     */
    private long startTime;
    protected volatile Status status;
    /**
     * CurrentThreadExecutor will exit the start function after this Future is completed
     */
    protected CompletableFuture<?> targetFuture;
    private long totalTaskTime;
    private final boolean recordTaskTime;

    public CurrentThreadExecutor(Runnable nextTick) {
        this(nextTick, false);
    }

    public CurrentThreadExecutor() {
        this(null, false);
    }

    /**
     * @param nextTick       nullable. it will be executed every time the queue is empty
     * @param recordTaskTime Whether to accumulate task execution time
     */
    public CurrentThreadExecutor(Runnable nextTick, boolean recordTaskTime) {
        this.tasks = new LinkedBlockingQueue<>();
        this.workerThread = Thread.currentThread();
        this.status = Status.READY;
        this.nextTick = nextTick;
        this.recordTaskTime = recordTaskTime;
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

    /**
     * @return Cumulative execution time of all tasks, if recordTaskTime is true
     */
    public long getTotalTaskTime() {
        return totalTaskTime;
    }

    public <T> T start(Supplier<CompletableFuture<T>> targetFuture, long timeout, TimeUnit timeUnit) throws InterruptedException {
        CompletableFuture<T> future = CompletableFuture.runAsync(() -> {
                }, this)
                .thenCompose(ignored -> targetFuture.get());
        return this.start(future, timeout, timeUnit);
    }

    /**
     * Execute the task until targetFuture is completed.
     * If there are remaining tasks when targetFuture is completed, Executor will not execute them, but they can be obtained through getTasks
     * Will be affected by interruptedException
     *
     * @param targetFuture Executor exit conditions
     * @param timeout      timeout
     * @param timeUnit     timeout unit
     * @param <T>          future type
     *
     * @return targetFuture.join()
     */
    public <T> T start(CompletableFuture<T> targetFuture, long timeout, TimeUnit timeUnit) throws InterruptedException {
        if (timeout > 0) {
            timeout = timeUnit.toMillis(timeout);
        }
        if (this.status == Status.RUNNING) {
            throw new UnexpectedStatusException("Executor is running");
        }
        if (!isWorkerThread()) {
            throw new NotWorkerThreadException();
        }
        this.status = Status.RUNNING;
        this.targetFuture = targetFuture;
        this.timeout = timeout;
        this.startTime = System.currentTimeMillis();

        while (!this.checkStatus()) {
            process();
        }
        if (AsyncHelper.isCompleted(targetFuture)) {
            return targetFuture.join();
        }
        log.warn("future not finished");
        return null;
    }

    public synchronized void reset(boolean clearTasks) {
        this.status = Status.READY;
        if (clearTasks) {
            this.tasks.clear();
        }
    }

    public <T> T start(CompletableFuture<T> targetFuture) throws InterruptedException {
        return this.start(targetFuture, -1, TimeUnit.MILLISECONDS);
    }

    public LinkedBlockingQueue<Runnable> getTasks() {
        return this.tasks;
    }

    /**
     * @return is finished or timeout
     */
    protected boolean checkStatus() {
        if (this.status.hasClosed()) {
            return true;
        }
        if (targetFuture == null || AsyncHelper.isCompleted(targetFuture)) {
            this.status = Status.COMPLETED;
        } else if (this.timeout > 0 && this.startTime > 0
                && (System.currentTimeMillis() - this.startTime > timeout)) {
            this.status = Status.TIMEOUT;
        }
        return this.status.hasClosed();
    }

    private void runTask(Runnable task) {
        if (task == null) {
            throw new NullPointerException();
        }
        if (this.recordTaskTime) {
            long startTime = System.currentTimeMillis();
            task.run();
            this.totalTaskTime += System.currentTimeMillis() - startTime;
        } else {
            task.run();
        }
    }

    protected void process() throws InterruptedException {
        try {
            long pollTimeout = 0;
            if (timeout > 0) {
                long elapse = System.currentTimeMillis() - this.startTime;
                if (elapse > timeout) {
                    return;
                }
                pollTimeout = timeout - elapse;
            }

            if (tasks.isEmpty() && nextTick != null) {
                this.runTask(this.nextTick);
            }

            Runnable task;
            if (pollTimeout > 0) {
                task = tasks.poll(pollTimeout, TimeUnit.MILLISECONDS);
            } else {
                task = tasks.take();
            }
            if (task != null) {
                this.runTask(task);
            }
        } catch (InterruptedException e) {
            this.status = Status.INTERRUPTED;
            throw e;
        }
    }
}
