package android.os;

/** The page's single main thread. Every callback from the browser enters the app through {@link #dispatch}. */
public final class Looper {
    private static final Looper MAIN = new Looper();
    private static final MessageQueue QUEUE = new MessageQueue();
    private static java.util.function.Consumer<Throwable> crashHandler;

    private Looper() {}

    public static Looper getMainLooper() { return MAIN; }
    public static Looper myLooper() { return MAIN; }
    public static MessageQueue myQueue() { return QUEUE; }
    public static void prepare() {}
    public static void loop() {}
    public MessageQueue getQueue() { return QUEUE; }
    public boolean isCurrentThread() { return true; }
    public Thread getThread() { return Thread.currentThread(); }
    public void quit() {}
    public void quitSafely() {}

    public static void setCrashHandler(java.util.function.Consumer<Throwable> handler) { crashHandler = handler; }

    /** Runs app code; an uncaught exception is reported like a crash instead of breaking the page's event loop. */
    public static void dispatch(Runnable r) {
        try {
            r.run();
        } catch (Throwable t) {
            if (crashHandler != null) crashHandler.accept(t); else t.printStackTrace();
        }
        QUEUE.scheduleIdle();
    }
}
