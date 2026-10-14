package android.os;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

/** Main-thread handler on browser timers; message order and removal follow Android's MessageQueue. */
public class Handler {
    @JSFunctor
    public interface Tick extends JSObject { void run(); }

    @JSBody(params = {"fn", "ms"}, script = "return setTimeout(fn, ms);")
    static native int setTimeout(Tick fn, int ms);

    @JSBody(params = "id", script = "clearTimeout(id);")
    static native void clearTimeout(int id);

    public interface Callback { boolean handleMessage(Message msg); }

    private static final java.util.ArrayList<Handler> ALL = new java.util.ArrayList<>();
    private final java.util.LinkedHashMap<Integer, Object[]> pending = new java.util.LinkedHashMap<>(); // id -> {runnable, token}
    private final Callback callback;

    public Handler() { this(Looper.getMainLooper(), null); }
    public Handler(Looper looper) { this(looper, null); }
    public Handler(Callback callback) { this(Looper.getMainLooper(), callback); }

    public Handler(Looper looper, Callback callback) {
        this.callback = callback;
        synchronized (ALL) { ALL.add(this); }
    }

    public static Handler createAsync(Looper looper) { return new Handler(looper); }

    public final Looper getLooper() { return Looper.getMainLooper(); }

    public final boolean post(Runnable r) { return postDelayed(r, 0); }

    public final boolean postDelayed(Runnable r, long delayMillis) { return postAtTime(r, null, SystemClock.uptimeMillis() + Math.max(0, delayMillis)); }

    public final boolean postDelayed(Runnable r, Object token, long delayMillis) { return postAtTime(r, token, SystemClock.uptimeMillis() + Math.max(0, delayMillis)); }

    public final boolean postAtTime(Runnable r, long uptimeMillis) { return postAtTime(r, null, uptimeMillis); }

    public final boolean postAtTime(Runnable r, Object token, long uptimeMillis) {
        if (r == null) return false;
        int[] idHolder = new int[1];
        long delay = Math.max(0, uptimeMillis - SystemClock.uptimeMillis());
        idHolder[0] = setTimeout(() -> {
            if (pending.remove(idHolder[0]) == null) return;
            Looper.dispatch(r);
        }, (int) Math.min(Integer.MAX_VALUE, delay));
        pending.put(idHolder[0], new Object[] {r, token});
        return true;
    }

    public final boolean postAtFrontOfQueue(Runnable r) { return post(r); }

    public final void removeCallbacks(Runnable r) { removeCallbacks(r, null); }

    public final void removeCallbacks(Runnable r, Object token) {
        java.util.Iterator<java.util.Map.Entry<Integer, Object[]>> it = pending.entrySet().iterator();
        while (it.hasNext()) {
            java.util.Map.Entry<Integer, Object[]> e = it.next();
            if (e.getValue()[0] == r && (token == null || e.getValue()[1] == token)) { clearTimeout(e.getKey()); it.remove(); }
        }
    }

    /** null removes every callback and message of this handler. */
    public final void removeCallbacksAndMessages(Object token) {
        java.util.Iterator<java.util.Map.Entry<Integer, Object[]>> it = pending.entrySet().iterator();
        while (it.hasNext()) {
            java.util.Map.Entry<Integer, Object[]> e = it.next();
            if (token == null || e.getValue()[1] == token) { clearTimeout(e.getKey()); it.remove(); }
        }
    }

    public final boolean hasCallbacks(Runnable r) {
        for (Object[] v : pending.values()) if (v[0] == r) return true;
        return false;
    }

    /** View.removeCallbacks may be called for a runnable posted through another handler of the page. */
    public static void removeEverywhere(Runnable r) {
        synchronized (ALL) { for (Handler h : ALL) h.removeCallbacks(r); }
    }

    // ------------------------------------------------------------------ messages
    public void handleMessage(Message msg) {}

    public void dispatchMessage(Message msg) {
        if (msg.callback != null) msg.callback.run();
        else if (callback == null || !callback.handleMessage(msg)) handleMessage(msg);
    }

    public final Message obtainMessage() { Message m = Message.obtain(); m.target = this; return m; }
    public final Message obtainMessage(int what) { Message m = obtainMessage(); m.what = what; return m; }
    public final Message obtainMessage(int what, Object obj) { Message m = obtainMessage(what); m.obj = obj; return m; }
    public final boolean sendMessage(Message msg) { return sendMessageDelayed(msg, 0); }
    public final boolean sendEmptyMessage(int what) { return sendMessage(obtainMessage(what)); }
    public final boolean sendEmptyMessageDelayed(int what, long delay) { return sendMessageDelayed(obtainMessage(what), delay); }

    public final boolean sendMessageDelayed(Message msg, long delayMillis) {
        Runnable r = () -> dispatchMessage(msg);
        return postDelayed(r, msg.what == 0 && msg.obj == null ? null : msg, delayMillis);
    }

    public final void removeMessages(int what) {
        java.util.Iterator<java.util.Map.Entry<Integer, Object[]>> it = pending.entrySet().iterator();
        while (it.hasNext()) {
            java.util.Map.Entry<Integer, Object[]> e = it.next();
            Object tok = e.getValue()[1];
            if (tok instanceof Message && ((Message) tok).what == what) { clearTimeout(e.getKey()); it.remove(); }
        }
    }

    public final boolean hasMessages(int what) {
        for (Object[] v : pending.values()) if (v[1] instanceof Message && ((Message) v[1]).what == what) return true;
        return false;
    }
}
