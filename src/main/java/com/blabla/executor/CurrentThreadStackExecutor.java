package com.blabla.executor;

import com.blabla.executor.exception.NotExpectedStatusException;
import com.blabla.executor.exception.NotWorkerThreadException;

import java.util.concurrent.CompletableFuture;

/**
 * 支持await Future
 */
public class CurrentThreadStackExecutor extends CurrentThreadExecutor {
    public CurrentThreadStackExecutor() {
        super();
    }

    /**
     * 只能等待处于当前Executor队列中的future，
     * 如果不再，请先submit
     *
     * @return null时，可能future结果为null，也可能是finalResult为null。需要自行判断处理。
     */
    public <T> T await(CompletableFuture<T> future) throws InterruptedException {
        if (this.status != Status.RUNNING) {
            throw new NotExpectedStatusException("CurrentThreadStackExecutor未处于运行中状态");
        }
        fallInLoop(future);
        // 此时，可能finalResult已经有结果，但是future是无关任务，而没有结果。
        // join可能会抛出CompletableFuture异常，但此处不捕获，继续向上抛出。
        if (futureHasDone(future)) {
            return future.join();
        }
        return null;
    }


    private void fallInLoop(CompletableFuture<?> future) throws InterruptedException {
        if (!isWorkerThread()) {
            throw new NotWorkerThreadException();
        }
        while (!checkStatus() && !futureHasDone(future)) {
            super.process();
        }
    }
}
