package com.blabla.executor;

import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;


@Slf4j
public class CurrentThreadExecutorErrorTest {
    @Test
    public void testMultiTasksOnCurrentThread() throws InterruptedException {
        // 每个请求耗时0s，这里是用Thread.Sleep模拟请求的，请求在当前线程中。
        final int requestSleepSecond = 0;
        final int count = 10;

        Thread currentThread = Thread.currentThread();
        CurrentThreadExecutor currentThreadExecutor = new CurrentThreadExecutor();

        List<CompletableFuture<String>> requests = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int index = i;
            CompletableFuture<String> request = MockRequest.requestOnCurrentPool.apply(requestSleepSecond, currentThreadExecutor)
//                    .thenApply(response -> {
//                        Assert.assertEquals(currentThread, Thread.currentThread());
//                        return response;
//                    })
                    .thenCompose(response -> {
                        Assert.assertEquals(currentThread, Thread.currentThread());
                        System.out.println(index + "before");
                        if (index == 0) {
                            throw new NullPointerException();
                        }
                        System.out.println(index);
                        return CompletableFuture.completedFuture(response);
                    });
//                    .handle((o, e) -> {
//                        return o;
//                    });
            requests.add(request);
        }

        CompletableFuture<List<String>> future = AsyncHelper.aggregate(requests);
        List<String> response = currentThreadExecutor.start(future);
        Assert.assertNotNull(response);
        Assert.assertEquals(response.size(), count);
    }
}
