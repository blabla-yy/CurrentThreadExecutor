package com.blabla.executor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class AsyncHelper {
    public static <S> CompletableFuture<S> toCompletableFuture(S t) {
        if (t instanceof CompletionStage) {
            //noinspection unchecked
            return ((CompletionStage<S>) t).toCompletableFuture();
        } else {
            return CompletableFuture.completedFuture(t);
        }
    }

    public static <V> CompletableFuture<List<V>> aggregate(List<CompletableFuture<V>> list) {
        final int size = list.size();
        return CompletableFuture.allOf(list.toArray(new CompletableFuture[0]))
                .thenApply(ignored -> {
                    List<V> result = new ArrayList<>(size);
                    for (CompletableFuture<V> value : list) {
                        result.add(value.join());
                    }
                    return result;
                });
    }

    public static CompletableFuture<Void> awaitAll(List<CompletableFuture<?>> list) {
        return CompletableFuture.allOf(list.toArray(new CompletableFuture[0]));
    }
}
