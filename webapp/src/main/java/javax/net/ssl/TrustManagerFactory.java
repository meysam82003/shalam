package javax.net.ssl;

public class TrustManagerFactory {
    public static final String getDefaultAlgorithm() { return "PKIX"; }
    public static final TrustManagerFactory getInstance(String algorithm) { return new TrustManagerFactory(); }
    public final void init(java.security.KeyStore ks) {}
    public final TrustManager[] getTrustManagers() { return new TrustManager[0]; }
}
