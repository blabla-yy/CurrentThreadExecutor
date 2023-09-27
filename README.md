## CurrentThreadExecutor

所有任务都在初始化CurrentThreadExecutor实例的线程中运行（即'当前线程'）。
能够处理异步任务且没有线程池，不会在Executor中创建线程，工作线程可控。

### 使用场景
- 单线程处理大量异步IO任务，__消除线程池配置困扰以及多线程问题__ 。
- 使用CompletableFuture等异步工具，希望任务全部执行在一个 __可控线程__ 。（目前JDK提供的Executor实现都是会创建线程的。）
- 使用JDK19+ VirtualThread同样可以指定此Executor，避免使用额外的线程资源。

### 特点

- 统计累计所有任务耗时。可以用来评估CPU计算耗时和IO耗时的占比。
- 支持interrupt中断。
- 超时终止执行。
- 支持类似Node.js next-tick回调

### 使用

#### CurrentThreadExecutor

```java
class Test {
  private final ExecutorService threadPoolExecutor = Executors.newFixedThreadPool(10);

  private CompletableFuture<String> requestOnThreadPool() {
    return CompletableFuture.supplyAsync(() -> "response", threadPoolExecutor);
  }

  @Test
  public void test() {
    final int count = 10;
    Thread mainThread = Thread.currentThread();
    CurrentThreadExecutor mainThreadExecutor = new CurrentThreadExecutor();

    List<CompletableFuture<String>> requests = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      CompletableFuture<String> request = this.requestOnThreadPool()
              .thenApplyAsync(response -> {
                Assert.assertEquals(mainThread, Thread.currentThread());
                return response;
              }, mainThreadExecutor); // back to main thread
      requests.add(request);
    }
    CompletableFuture<List<String>> targetFuture = AsyncHelper.aggregate(requests);
    // Start executing all tasks until targetFuture is completed.
    List<String> response = mainThreadExecutor.start(targetFuture);

    Assert.assertNotNull(response);
    Assert.assertEquals(response.size(), count);
  }
}

```

---

All tasks run in the thread that initialized the CurrentThreadExecutor instance (i.e. the 'current thread').
Able to handle asynchronous tasks without a thread pool, no threads will be created in the Executor, and the worker threads are controllable.

### Scenes to be used

- Single thread handles a large number of asynchronous IO tasks, __eliminating thread pool configuration troubles and multi-thread competition problems__
- Using asynchronous tools such as CompletableFuture, it is hoped that all tasks will be executed in one __controllable thread__. (The current Executor implementation provided by JDK all creates threads.)
- Using JDK19+ VirtualThread can also specify this Executor and avoid multi-thread problem.

### Features

- Count the time spent on all tasks. It can be used to evaluate the proportion of CPU calculation time and IO time consumption.
- interrupt.
- Timeout terminates execution.
- Support similar to Node.js next-tick callback