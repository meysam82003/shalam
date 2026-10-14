package org.teavm.classlib.java.util.concurrent;

/** Thread pools on TeaVM green threads: tasks run in order on worker threads that wait while the queue is empty. */
public final class TExecutors {
    private TExecutors() {}

    public static TExecutorService newSingleThreadExecutor() { return new Pool(1); }
    public static TExecutorService newFixedThreadPool(int nThreads) { return new Pool(Math.max(1, nThreads)); }
    public static TExecutorService newCachedThreadPool() { return new Pool(4); }

    static final class Pool implements TExecutorService {
        private final java.util.ArrayDeque<Runnable> queue = new java.util.ArrayDeque<>();
        private final Object lock = new Object();
        private boolean shutdown;
        private int running;

        Pool(int threads) {
            for (int i = 0; i < threads; i++) {
                Thread t = new Thread(this::work);
                t.setDaemon(true);
                t.start();
            }
        }

        private void work() {
            while (true) {
                Runnable r;
                synchronized (lock) {
                    while (queue.isEmpty() && !shutdown) {
                        try { lock.wait(); } catch (InterruptedException e) { return; }
                    }
                    if (queue.isEmpty()) return;
                    r = queue.poll();
                    running++;
                }
                try {
                    r.run();
                } catch (Throwable t) {
                    t.printStackTrace();
                } finally {
                    synchronized (lock) { running--; }
                }
            }
        }

        @Override public void execute(Runnable command) {
            if (command == null) throw new NullPointerException();
            synchronized (lock) {
                if (shutdown) throw new java.util.concurrent.RejectedExecutionException();
                queue.add(command);
                lock.notifyAll();
            }
        }

        @Override public void shutdown() { synchronized (lock) { shutdown = true; lock.notifyAll(); } }

        @Override public java.util.List<Runnable> shutdownNow() {
            synchronized (lock) {
                shutdown = true;
                java.util.List<Runnable> rest = new java.util.ArrayList<>(queue);
                queue.clear();
                lock.notifyAll();
                return rest;
            }
        }

        @Override public boolean isShutdown() { return shutdown; }
        @Override public boolean isTerminated() { return shutdown && queue.isEmpty() && running == 0; }

        @Override public TFuture<?> submit(Runnable task) { return submit(() -> { task.run(); return null; }); }

        @Override public <T> TFuture<T> submit(java.util.concurrent.Callable<T> task) {
            Task<T> f = new Task<>(task);
            execute(f);
            return f;
        }
    }

    static final class Task<V> implements TFuture<V>, Runnable {
        private final java.util.concurrent.Callable<V> callable;
        private V value;
        private Throwable error;
        private boolean done, cancelled;

        Task(java.util.concurrent.Callable<V> c) { callable = c; }

        @Override public void run() {
            if (cancelled) return;
            try { value = callable.call(); } catch (Throwable t) { error = t; }
            synchronized (this) { done = true; notifyAll(); }
        }

        @Override public boolean cancel(boolean mayInterruptIfRunning) { if (done) return false; cancelled = true; return true; }
        @Override public boolean isCancelled() { return cancelled; }
        @Override public boolean isDone() { return done || cancelled; }

        @Override public synchronized V get() throws InterruptedException, java.util.concurrent.ExecutionException {
            while (!done) wait();
            if (error != null) throw new java.util.concurrent.ExecutionException(error);
            return value;
        }
    }
}
