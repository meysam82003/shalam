package android.os;

public final class Message {
    public int what, arg1, arg2;
    public Object obj;
    Runnable callback;
    Handler target;

    public static Message obtain() { return new Message(); }
    public static Message obtain(Handler h, int what) { Message m = new Message(); m.target = h; m.what = what; return m; }
    public void sendToTarget() { if (target != null) target.sendMessage(this); }
    public void recycle() {}
}
