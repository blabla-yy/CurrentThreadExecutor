## CurrentThreadExecutor

所有任务都在初始化CurrentThreadExecutor实例的线程中运行（即'当前线程'）。
能够处理异步任务且没有线程池，不会在Executor中创建线程，工作线程可控。

### 使用场景
- 单线程处理大量异步IO任务，__消除线程池配置困扰以及多线程问题__ 。
- 使用CompletableFuture等异步工具，希望任务全部执行在一个 __可控线程__ 。（目前JDK提供的Executor实现都是会创建线程的。）
- 使用JDK19+ VirtualThread同样可以指定此Executor，避免使用额外的线程资源。

### 特点

- 无创建线程，当前线程处理所有异步任务。
- 统计累计所有任务耗时。可以用来评估CPU计算耗时和IO耗时的占比。
- 支持interrupt中断。
- 超时终止执行。
- 支持类似Node.js next-tick回调。
- JDK8+

### 使用

#### CurrentThreadExecutor
只使用主线程发起、处理并等待100个异步请求结束

Only use the main thread to initiate, process and wait for the end of 100 asynchronous requests
```java
import java.util.concurrent.CompletableFuture;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

class Test {
    public void httpRequest() {
        var currentThreadExecutor = new CurrentThreadExecutor();

        // HttpClient JDK11+
        var client = HttpClient.newBuilder()
                .executor(currentThreadExecutor) //selector executor default is: Executors.newCachedThreadPool(new DefaultThreadFactory(id));
                .build();
        var futures = new ArrayList<CompletableFuture<HttpResponse<String>>>();

        for (int i = 0; i < 100; i++) {
            final int index = i;
            var request = HttpRequest.newBuilder()
                    .uri(URI.create("http://0.0.0.0"))
                    .build();
            var future = client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .whenCompleteAsync((response, exception) -> {
                        // main thread
                        System.out.println("Request: " + index + " completed on " + Thread.currentThread().getName());
                    }, currentThreadExecutor); // default is ForkJoinPool
            futures.add(future);
        }
        var allRequestsFuture = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        currentThreadExecutor.start(allRequestsFuture); // Start processing all tasks until the Future is completed.
        System.out.println("All tasks have been completed");
    }
}
```

Used with CompletableFuture
```java
import java.util.concurrent.CompletableFuture;

class Test {
    // The main thread executes all CompletableFuture tasks, avoiding the use of ForkJoinPool
    public void usingCompletableFuture() {
        final int count = 10;
        Thread mainThread = Thread.currentThread();
        CurrentThreadExecutor mainThreadExecutor = new CurrentThreadExecutor();
        List<CompletableFuture<String>> requests = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            CompletableFuture<String> request = CompletableFuture.runAsync(() -> "Hello", mainThreadExecutor) // Go back to the main thread and avoid using ForkJoinPool
                    .thenApplyAsync(response -> {
                        Assert.assertEquals(mainThread, Thread.currentThread());
                        return response;
                    }, mainThreadExecutor); // default is ForkJoinPool
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

- No thread is created, the current thread handles all asynchronous tasks
- Count the time spent on all tasks. It can be used to evaluate the proportion of CPU calculation time and IO time consumption.
- interrupt.
- Timeout terminates execution.
- Support similar to Node.js next-tick callback
- JDK8+