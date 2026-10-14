package org.teavm.classlib.javax.net.ssl;

public abstract class THttpsURLConnection extends java.net.HttpURLConnection {
    protected THttpsURLConnection(java.net.URL url) { super(url); }
    public void setSSLSocketFactory(TSSLSocketFactory sf) {}
    public TSSLSocketFactory getSSLSocketFactory() { return null; }
}
