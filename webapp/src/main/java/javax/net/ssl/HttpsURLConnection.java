package javax.net.ssl;

public abstract class HttpsURLConnection extends java.net.HttpURLConnection {
    protected HttpsURLConnection(java.net.URL url) { super(url); }
    public void setSSLSocketFactory(SSLSocketFactory sf) {}
    public SSLSocketFactory getSSLSocketFactory() { return null; }
}
