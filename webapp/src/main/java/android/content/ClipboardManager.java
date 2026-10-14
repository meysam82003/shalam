package android.content;

public class ClipboardManager {
    private ClipData primary;

    public void setPrimaryClip(ClipData clip) {
        primary = clip;
        CharSequence t = clip.getItemAt(0).getText();
        if (t != null) divan.web.Host.copyText(t.toString());
    }

    public ClipData getPrimaryClip() { return primary; }
    public boolean hasPrimaryClip() { return primary != null; }
}
