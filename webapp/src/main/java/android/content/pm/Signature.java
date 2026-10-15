package android.content.pm;

/** An APK signing certificate; the web version has none. */
public class Signature {
    private final byte[] bytes;
    public Signature(byte[] bytes) { this.bytes = bytes; }
    public byte[] toByteArray() { return bytes.clone(); }
}
