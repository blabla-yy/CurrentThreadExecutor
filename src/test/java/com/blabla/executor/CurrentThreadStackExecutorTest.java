package com.blabla.executor;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;


public class CurrentThreadStackExecutorTest {

    @Test
    public void testThreadSwitch() throws InterruptedException {
        // 每个请求耗时1s
        int requestSleepSecond = 1;
        Thread currentThread = Thread.currentThread();

        CurrentThreadStackExecutor stackExecutor = new CurrentThreadStackExecutor();

        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
            CompletableFuture<String> request = MockRequest.requestOnCurrentPool.apply(requestSleepSecond, stackExecutor);
            try {
                String await = stackExecutor.await(request);
                Assert.assertEquals(currentThread, Thread.currentThread());
                Assert.assertNotNull(await);
                return await;
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }, stackExecutor);

        String response = stackExecutor.start(future);
        Assert.assertNotNull(response);
        System.out.println(response);
    }
}
