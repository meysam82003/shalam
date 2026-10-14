package android.content;

public class ClipData {
    public static class Item {
        final CharSequence text;
        final android.net.Uri uri;
        public Item(CharSequence text) { this.text = text; this.uri = null; }
        public Item(android.net.Uri uri) { this.text = null; this.uri = uri; }
        public CharSequence getText() { return text; }
        public android.net.Uri getUri() { return uri; }
    }

    private final CharSequence label;
    private final Item item;

    private ClipData(CharSequence label, Item item) { this.label = label; this.item = item; }

    public static ClipData newPlainText(CharSequence label, CharSequence text) { return new ClipData(label, new Item(text)); }
    public static ClipData newRawUri(CharSequence label, android.net.Uri uri) { return new ClipData(label, new Item(uri)); }
    public int getItemCount() { return 1; }
    public Item getItemAt(int index) { return item; }
    public CharSequence label() { return label; }
}
