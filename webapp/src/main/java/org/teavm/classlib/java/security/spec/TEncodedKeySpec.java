package org.teavm.classlib.java.security.spec;

public abstract class TEncodedKeySpec implements TKeySpec {
    private final byte[] encoded;
    public TEncodedKeySpec(byte[] encodedKey) { encoded = encodedKey.clone(); }
    public byte[] getEncoded() { return encoded.clone(); }
    public abstract String getFormat();
}
