package org.teavm.classlib.java.util.concurrent;

/** java.util.concurrent.ExecutorService for the browser build (green threads of TeaVM). */
public interface TExecutorService extends java.util.concurrent.Executor {
    void shutdown();
    java.util.List<Runnable> shutdownNow();
    boolean isShutdown();
    boolean isTerminated();
    TFuture<?> submit(Runnable task);
    <T> TFuture<T> submit(java.util.concurrent.Callable<T> task);
}
