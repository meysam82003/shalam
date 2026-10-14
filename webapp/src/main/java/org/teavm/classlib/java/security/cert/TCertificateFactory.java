package org.teavm.classlib.java.security.cert;

public class TCertificateFactory {
    public static final TCertificateFactory getInstance(String type) { return new TCertificateFactory(); }

    public final TCertificate generateCertificate(java.io.InputStream in) {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        try {
            byte[] b = new byte[4096];
            int n;
            while ((n = in.read(b)) > 0) out.write(b, 0, n);
        } catch (java.io.IOException e) { /* empty certificate */ }
        byte[] data = out.toByteArray();
        return new TCertificate("X.509") { @Override public byte[] getEncoded() { return data.clone(); } };
    }
}
