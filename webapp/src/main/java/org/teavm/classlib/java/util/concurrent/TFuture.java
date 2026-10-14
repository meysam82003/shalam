package org.teavm.classlib.java.util.concurrent;

public interface TFuture<V> {
    boolean cancel(boolean mayInterruptIfRunning);
    boolean isCancelled();
    boolean isDone();
    V get() throws InterruptedException, java.util.concurrent.ExecutionException;
}
