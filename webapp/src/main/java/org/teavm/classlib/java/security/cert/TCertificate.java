package org.teavm.classlib.java.security.cert;

public abstract class TCertificate {
    private final String type;
    protected TCertificate(String type) { this.type = type; }
    public final String getType() { return type; }
    public abstract byte[] getEncoded();
}
