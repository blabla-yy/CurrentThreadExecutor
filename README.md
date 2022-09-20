# CurrentThreadExecutor

所有任务都在初始化CurrentThreadExecutor实例的线程中运行（即'当前线程'）。
能够处理异步任务且没有线程池，工作线程可控。

# 使用场景

- 使用CompletableFuture等异步工具，希望任务全部执行在一个 __可控线程__ 。
- 使用Tomcat、Jetty等web容器，每个请求线程分别处理大量异步IO任务，相互隔离线程资源，__消除线程池配置困扰以及多线程竞争问题__ 。

# 特点

- 支持超时设置
- 支持中断执行器
- CurrentThreadStackExecutor 支持await函数。

# 使用

## CurrentThreadExecutor

```java
class Test {
    public void test() {
        final int requestSleepSecond = 0;
        final int count = 10;
        Thread currentThread = Thread.currentThread();
        CurrentThreadExecutor currentThreadExecutor = new CurrentThreadExecutor();

        List<CompletableFuture<String>> requests = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            // 指定Executor。所有的异步任务均会在当前线程中执行，没有多线程。
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

```

- 使用CompletableFuture的async为后缀函数，可以指定executor，将其指定为CurrentThreadExecutor实例。
    - 如果前置任务已经在当前线程了，后续直接不使用async函数切换线程。
- 获取直接使用CurrentThreadExecutor.submit()函数提交任务。
- 结束标志
  - start需要传一个future参数，只要此future完成，Executor就会结束，退出函数。如果有多余的任务未执行则抛弃。

## CurrentThreadStackExecutor 支持await

```java
class Test {
    private String handle(CurrentThreadStackExecutor stackExecutor) {
        CompletableFuture<String> request = MockRequest.requestOnCurrentPool.apply(requestSleepSecond, stackExecutor);
        try {
            String await = stackExecutor.await(request); // 提交到stackExecutor的任务。可以await future
            Assert.assertEquals(currentThread, Thread.currentThread());
            Assert.assertNotNull(await);
            return await;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public void testThreadSwitch() throws InterruptedException {
        // 每个请求耗时1s
        int requestSleepSecond = 1;
        Thread currentThread = Thread.currentThread();

        CurrentThreadStackExecutor stackExecutor = new CurrentThreadStackExecutor();

        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> handle(stackExecutor), stackExecutor);

        String response = stackExecutor.start(future);
        Assert.assertNotNull(response);
        System.out.println(response);
    }
}
```
- 基于栈实现。所以时间/空间都不会是最优的
- 可控的异步任务，使用await函数，可以简化代码，避免回调地狱。