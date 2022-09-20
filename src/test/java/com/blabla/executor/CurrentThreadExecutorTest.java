package com.blabla.executor;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;


public class CurrentThreadExecutorTest {

    @Test
    public void testThreadSwitch() throws InterruptedException {
        // 每个请求耗时1s
        int requestSleepSecond = 1;

        Thread currentThread = Thread.currentThread();

        CurrentThreadExecutor currentThreadExecutor = new CurrentThreadExecutor();
        CompletableFuture<String> requestA = MockRequest.requestOnOtherThreadPool.apply(requestSleepSecond)
                .thenApplyAsync(response -> {
                    Assert.assertEquals(currentThread, Thread.currentThread());
                    return response;
                }, currentThreadExecutor)
                .thenApply(response -> {
                    Assert.assertEquals(currentThread, Thread.currentThread());
                    return response;
                });

        String response = currentThreadExecutor.start(requestA);
        Assert.assertNotNull(response);
        System.out.println(response);
    }

    @Test
    public void testMultiTasks() throws InterruptedException {
        // 每个请求耗时1s
        final int requestSleepSecond = 1;
        final int count = 10;

        Thread currentThread = Thread.currentThread();
        CurrentThreadExecutor currentThreadExecutor = new CurrentThreadExecutor();

        List<CompletableFuture<String>> requests = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            CompletableFuture<String> request = MockRequest.requestOnOtherThreadPool.apply(requestSleepSecond)
                    .thenApplyAsync(response -> {
                        Assert.assertEquals(currentThread, Thread.currentThread());
                        return response;
                    }, currentThreadExecutor)
                    .thenApply(response -> {
                        Assert.assertEquals(currentThread, Thread.currentThread());
                        return response;
                    });
            requests.add(request);
        }
        CompletableFuture<List<String>> future = AsyncHelper.aggregate(requests);
        List<String> response = currentThreadExecutor.start(future);
        Assert.assertNotNull(response);
        Assert.assertEquals(response.size(), count);
    }

    @Test
    public void testMultiTasksOnCurrentThread() throws InterruptedException {
        // 每个请求耗时0s，这里是用Thread.Sleep模拟请求的，请求在当前线程中。
        final int requestSleepSecond = 0;
        final int count = 10;

        Thread currentThread = Thread.currentThread();
        CurrentThreadExecutor currentThreadExecutor = new CurrentThreadExecutor();

        List<CompletableFuture<String>> requests = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            CompletableFuture<String> request = MockRequest.requestOnCurrentPool.apply(requestSleepSecond, currentThreadExecutor)
                    .thenApply(response -> {
                        Assert.assertEquals(currentThread, Thread.currentThread());
                        return response;
                    })
                    .thenApply(response -> {
                        Assert.assertEquals(currentThread, Thread.currentThread());
                        return response;
                    });
            requests.add(request);
        }
        CompletableFuture<List<String>> future = AsyncHelper.aggregate(requests);
        List<String> response = currentThreadExecutor.start(future);
        Assert.assertNotNull(response);
        Assert.assertEquals(response.size(), count);
    }
}
