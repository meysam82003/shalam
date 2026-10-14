package android.os;

/** Idle handlers run when the page has nothing else to do (browser idle callback). */
public final class MessageQueue {
    public interface IdleHandler { boolean queueIdle(); }

    private final java.util.ArrayList<IdleHandler> idle = new java.util.ArrayList<>();
    private boolean scheduled;

    public void addIdleHandler(IdleHandler handler) {
        if (handler == null) throw new NullPointerException("Can't add a null IdleHandler");
        idle.add(handler);
        scheduleIdle();
    }

    public void removeIdleHandler(IdleHandler handler) { idle.remove(handler); }

    public boolean isIdle() { return true; }

    void scheduleIdle() {
        if (scheduled || idle.isEmpty()) return;
        scheduled = true;
        divan.web.Idle.request(this::runIdle);
    }

    private void runIdle() {
        scheduled = false;
        for (IdleHandler h : new java.util.ArrayList<>(idle)) {
            boolean keep;
            try { keep = h.queueIdle(); } catch (Throwable t) { keep = false; t.printStackTrace(); }
            if (!keep) idle.remove(h);
        }
        scheduleIdle();
    }
}
