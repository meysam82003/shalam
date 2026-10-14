package org.teavm.classlib.javax.net.ssl;

public class TTrustManagerFactory {
    public static final String getDefaultAlgorithm() { return "PKIX"; }
    public static final TTrustManagerFactory getInstance(String algorithm) { return new TTrustManagerFactory(); }
    public final void init(java.security.KeyStore ks) {}
    public final TTrustManager[] getTrustManagers() { return new TTrustManager[0]; }
}
