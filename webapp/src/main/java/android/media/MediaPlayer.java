package android.media;

/** Sound playback through the page's audio (Web Audio); raw resources and recorded/picked files. */
public class MediaPlayer {
    public interface OnCompletionListener { void onCompletion(MediaPlayer mp); }
    public interface OnPreparedListener { void onPrepared(MediaPlayer mp); }
    public interface OnErrorListener { boolean onError(MediaPlayer mp, int what, int extra); }

    private int handle = -1;
    private OnCompletionListener completion;
    private boolean looping;
    private float volume = 1f;

    public MediaPlayer() {}

    private MediaPlayer(int handle) { this.handle = handle; }

    public static MediaPlayer create(android.content.Context context, int resid) {
        String url = divan.web.Res.rawUrl(resid);
        if (url == null) return null;
        return new MediaPlayer(divan.web.Sound.load(url, null));
    }

    public static MediaPlayer create(android.content.Context context, android.net.Uri uri) {
        if (uri == null) return null;
        if ("file".equals(uri.getScheme())) {
            byte[] data = divan.web.Storage.readFile(uri.getPath());
            if (data == null) return null;
            return new MediaPlayer(divan.web.Sound.load(null, data));
        }
        byte[] picked = divan.web.Picked.bytes(uri);
        if (picked != null) return new MediaPlayer(divan.web.Sound.load(null, picked));
        return new MediaPlayer(divan.web.Sound.load(uri.toString(), null));
    }

    public void setDataSource(String path) {
        byte[] data = divan.web.Storage.readFile(path);
        handle = data != null ? divan.web.Sound.load(null, data) : divan.web.Sound.load(path, null);
    }

    public void setDataSource(android.content.Context context, android.net.Uri uri) { MediaPlayer p = create(context, uri); if (p != null) handle = p.handle; }
    public void prepare() {}
    public void prepareAsync() {}
    public void start() { if (handle >= 0) divan.web.Sound.play(handle, looping, volume, () -> { if (completion != null && !looping) completion.onCompletion(this); }); }
    public void pause() { if (handle >= 0) divan.web.Sound.stop(handle); }
    public void stop() { if (handle >= 0) divan.web.Sound.stop(handle); }
    public void release() { if (handle >= 0) divan.web.Sound.release(handle); handle = -1; }
    public void reset() { stop(); }
    public boolean isPlaying() { return handle >= 0 && divan.web.Sound.playing(handle); }
    public void setLooping(boolean looping) { this.looping = looping; }
    public boolean isLooping() { return looping; }
    public void setVolume(float left, float right) { volume = Math.max(left, right); if (handle >= 0) divan.web.Sound.volume(handle, volume); }
    public void setOnCompletionListener(OnCompletionListener listener) { completion = listener; }
    public void setOnPreparedListener(OnPreparedListener listener) {}
    public void setOnErrorListener(OnErrorListener listener) {}
    public void setAudioStreamType(int streamtype) {}
    public int getDuration() { return handle >= 0 ? divan.web.Sound.duration(handle) : 0; }
    public int getCurrentPosition() { return 0; }
    public void seekTo(int msec) {}
}
