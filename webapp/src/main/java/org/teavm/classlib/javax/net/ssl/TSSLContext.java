package org.teavm.classlib.javax.net.ssl;

public class TSSLContext {
    public static TSSLContext getInstance(String protocol) { return new TSSLContext(); }
    public final void init(TKeyManager[] km, TTrustManager[] tm, java.security.SecureRandom random) {}
    public final TSSLSocketFactory getSocketFactory() { return new TSSLSocketFactory() {}; }
}
