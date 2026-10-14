package android.content.pm;

/** Signing details of an installed package; the web version reports none. */
public class SigningInfo {
    public boolean hasMultipleSigners() { return false; }
    public Signature[] getApkContentsSigners() { return new Signature[0]; }
    public Signature[] getSigningCertificateHistory() { return new Signature[0]; }
}
