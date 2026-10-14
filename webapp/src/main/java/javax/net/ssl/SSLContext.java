package javax.net.ssl;

public class SSLContext {
    public static SSLContext getInstance(String protocol) { return new SSLContext(); }
    public final void init(KeyManager[] km, TrustManager[] tm, java.security.SecureRandom random) {}
    public final SSLSocketFactory getSocketFactory() { return new SSLSocketFactory() {}; }
}
