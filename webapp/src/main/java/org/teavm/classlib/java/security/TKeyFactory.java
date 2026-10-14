package org.teavm.classlib.java.security;

public class TKeyFactory {
    private TKeyFactory() {}

    public static TKeyFactory getInstance(String algorithm) throws TNoSuchAlgorithmException {
        if (!"RSA".equalsIgnoreCase(algorithm)) throw new TNoSuchAlgorithmException(algorithm);
        return new TKeyFactory();
    }

    public final TPublicKey generatePublic(org.teavm.classlib.java.security.spec.TKeySpec keySpec) throws org.teavm.classlib.java.security.spec.TInvalidKeySpecException {
        if (!(keySpec instanceof org.teavm.classlib.java.security.spec.TX509EncodedKeySpec)) throw new org.teavm.classlib.java.security.spec.TInvalidKeySpecException("X.509 only");
        try {
            return new TRsaPublicKey(((org.teavm.classlib.java.security.spec.TX509EncodedKeySpec) keySpec).getEncoded());
        } catch (TGeneralSecurityException e) {
            throw new org.teavm.classlib.java.security.spec.TInvalidKeySpecException(e.getMessage());
        }
    }
}
