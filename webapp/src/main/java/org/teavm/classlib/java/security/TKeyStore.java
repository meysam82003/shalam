package org.teavm.classlib.java.security;

/** The browser checks HTTPS certificates itself; this store only keeps what the app adds. */
public class TKeyStore {
    private final java.util.LinkedHashMap<String, org.teavm.classlib.java.security.cert.TCertificate> certs = new java.util.LinkedHashMap<>();

    public static TKeyStore getInstance(String type) throws TKeyStoreException { return new TKeyStore(); }
    public static String getDefaultType() { return "BKS"; }
    public final void load(java.io.InputStream stream, char[] password) {}
    public final void setCertificateEntry(String alias, org.teavm.classlib.java.security.cert.TCertificate cert) { certs.put(alias, cert); }
    public final org.teavm.classlib.java.security.cert.TCertificate getCertificate(String alias) { return certs.get(alias); }
    public final java.util.Enumeration<String> aliases() { return java.util.Collections.enumeration(certs.keySet()); }
}
