package divan.web;

import android.content.Intent;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSByRef;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

/** startActivity / startActivityForResult in the browser: links, sharing, the file picker and saving a file. */
public final class Intents {
    private Intents() {}

    @JSFunctor interface Picked extends JSObject { void call(String name, String mime, @JSByRef byte[] data); }

    @JSBody(params = "u", script = "DH.openLink(u);")
    private static native void openLink(String url);

    @JSBody(params = {"name", "mime", "data", "text"}, script = "DH.share(name, mime, data, text);")
    private static native void share(String name, String mime, @JSByRef byte[] data, String text);

    @JSBody(params = "text", script = "DH.shareText(text);")
    private static native void shareText(String text);

    @JSBody(params = {"accept", "cb"}, script = "DH.pick(accept, cb);")
    private static native void pick(String accept, Picked cb);

    @JSBody(params = {"name", "mime", "data"}, script = "DH.download(name, mime, data);")
    private static native void download0(String name, String mime, @JSByRef byte[] data);

    static void download(String name, String mime, byte[] data) { download0(name, mime == null ? "application/octet-stream" : mime, data); }

    public static void start(android.content.Context context, Intent intent, int requestCode) {
        if (intent == null) return;
        String action = intent.getAction();
        if (Intent.ACTION_CHOOSER.equals(action)) {
            android.os.Parcelable inner = intent.getExtras().getParcelable(Intent.EXTRA_INTENT);
            if (inner instanceof Intent) start(context, (Intent) inner, requestCode);
            return;
        }
        if (intent.getComponent() != null && action == null) { Main.newIntent(intent); return; }
        if (Intent.ACTION_VIEW.equals(action) || Intent.ACTION_SENDTO.equals(action)) {
            android.net.Uri u = intent.getData();
            if (u == null) throw new android.content.ActivityNotFoundException("no data");
            String s = u.toString();
            if (s.startsWith("divan://")) { Main.newIntent(intent); return; }
            if (s.startsWith("mailto:")) {
                String subject = intent.getStringExtra(Intent.EXTRA_SUBJECT), body = intent.getStringExtra(Intent.EXTRA_TEXT);
                StringBuilder q = new StringBuilder();
                if (subject != null) q.append(q.length() == 0 ? '?' : '&').append("subject=").append(enc(subject));
                if (body != null) q.append(q.length() == 0 ? '?' : '&').append("body=").append(enc(body));
                s = (s.contains("?") ? s : s + q);
            }
            if (!(s.startsWith("https://") || s.startsWith("http://") || s.startsWith("mailto:") || s.startsWith("tel:"))) throw new android.content.ActivityNotFoundException(s);
            openLink(s);
            return;
        }
        if (Intent.ACTION_SEND.equals(action)) {
            android.os.Parcelable stream = intent.getExtras().getParcelable(Intent.EXTRA_STREAM);
            String text = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (stream instanceof android.net.Uri) {
                android.net.Uri u = (android.net.Uri) stream;
                byte[] data = divan.web.Picked.bytes(u);
                if (data == null) throw new android.content.ActivityNotFoundException("missing file");
                share(u.getLastPathSegment(), intent.getType() == null ? "application/octet-stream" : intent.getType(), data, text == null ? "" : text);
            } else {
                shareText(text == null ? "" : text);
            }
            return;
        }
        android.app.Activity a = WebWindow.activity();
        if (Intent.ACTION_OPEN_DOCUMENT.equals(action) || Intent.ACTION_GET_CONTENT.equals(action)) {
            String[] types = intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES);
            String accept = types != null ? String.join(",", types) : intent.getType() != null && !"*/*".equals(intent.getType()) ? intent.getType() : "";
            pick(accept, (name, mime, data) -> android.os.Looper.dispatch(() -> {
                if (a == null || requestCode < 0) return;
                if (data == null) { a.dispatchActivityResult(requestCode, android.app.Activity.RESULT_CANCELED, null); return; }
                byte[] copy = java.util.Arrays.copyOf(data, data.length);
                android.net.Uri uri = divan.web.Picked.add("picked", name, mime, copy);
                Runnable deliver = () -> a.dispatchActivityResult(requestCode, android.app.Activity.RESULT_OK, new Intent().setData(uri));
                // Pictures are decoded before the app reads them (BitmapFactory is synchronous on Android).
                if (mime != null && mime.startsWith("image/")) JS.decodeThen(android.graphics.BitmapFactory.key(copy, 0, copy.length), copy, () -> android.os.Looper.dispatch(deliver));
                else deliver.run();
            }));
            return;
        }
        if (Intent.ACTION_CREATE_DOCUMENT.equals(action)) {
            String name = intent.getStringExtra(Intent.EXTRA_TITLE);
            android.net.Uri uri = divan.web.Picked.add("save", name == null ? "divan.json" : name, intent.getType(), new byte[0]);
            if (a != null && requestCode >= 0) {
                android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
                h.post(() -> a.dispatchActivityResult(requestCode, android.app.Activity.RESULT_OK, new Intent().setData(uri)));
            }
            return;
        }
        throw new android.content.ActivityNotFoundException(String.valueOf(action));
    }

    @JSBody(params = "s", script = "return encodeURIComponent(s);")
    private static native String enc(String s);
}
