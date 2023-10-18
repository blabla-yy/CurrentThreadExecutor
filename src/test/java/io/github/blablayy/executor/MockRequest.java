package io.github.blablayy.executor;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.function.BiFunction;
import java.util.function.Function;

public class MockRequest {
    public static Function<Integer, CompletableFuture<String>> requestOnOtherThreadPool = (sleep) -> {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(sleep * 1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return "requestOnOtherThreadPool sleep 1s";
        }, ForkJoinPool.commonPool());
    };

    public static BiFunction<Integer, Executor, CompletableFuture<String>> requestOnCurrentPool = (sleep, executor) -> {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Thread.sleep(sleep * 1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return "requestOnOtherThreadPool sleep 1s";
        }, executor);
    };
}
