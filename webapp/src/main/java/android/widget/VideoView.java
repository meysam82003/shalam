package android.widget;

import android.content.Context;
import android.media.MediaPlayer;
import android.view.View;

/** Video of an ad: the browser window shows a muted, inline video element over this view's bounds. */
public class VideoView extends View {
    private String path;
    private MediaPlayer.OnPreparedListener prepared;
    private MediaPlayer.OnCompletionListener completion;
    private boolean playing;

    public VideoView(Context context) { super(context); }

    public void setVideoPath(String path) { this.path = path; divan.web.Videos.update(this); }
    public void setVideoURI(android.net.Uri uri) { setVideoPath(uri.toString()); }
    public void setOnPreparedListener(MediaPlayer.OnPreparedListener l) { prepared = l; }
    public void setOnCompletionListener(MediaPlayer.OnCompletionListener l) { completion = l; }
    public void setOnErrorListener(MediaPlayer.OnErrorListener l) {}
    public void start() { playing = true; divan.web.Videos.update(this); }
    public void pause() { playing = false; divan.web.Videos.update(this); }
    public void stopPlayback() { playing = false; divan.web.Videos.remove(this); }
    public boolean isPlaying() { return playing; }
    public String videoPath() { return path; }
    public boolean wantsPlay() { return playing; }
    public void firePrepared() { if (prepared != null) prepared.onPrepared(new MediaPlayer()); }
    public void fireCompletion() { if (completion != null) completion.onCompletion(new MediaPlayer()); }

    @Override protected void onMeasure(int w, int h) { setMeasuredDimension(getDefaultSize(getSuggestedMinimumWidth(), w), getDefaultSize(getSuggestedMinimumHeight(), h)); }

    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); divan.web.Videos.update(this); }

    @Override protected void onDetachedFromWindow() { divan.web.Videos.remove(this); super.onDetachedFromWindow(); }
}
