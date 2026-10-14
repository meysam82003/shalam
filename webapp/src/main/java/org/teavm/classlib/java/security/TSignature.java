package org.teavm.classlib.java.security;

/** SHA256withRSA verification (PKCS #1 v1.5), the only signature the app checks (server licence certificates). */
public class TSignature {
    private static final byte[] SHA256_PREFIX = {0x30, 0x31, 0x30, 0x0d, 0x06, 0x09, 0x60, (byte) 0x86, 0x48, 0x01, 0x65, 0x03, 0x04, 0x02, 0x01, 0x05, 0x00, 0x04, 0x20};

    private TRsaPublicKey key;
    private final TMessageDigest digest;

    private TSignature() throws TNoSuchAlgorithmException { digest = TMessageDigest.getInstance("SHA-256"); }

    public static TSignature getInstance(String algorithm) throws TNoSuchAlgorithmException {
        if (!"SHA256withRSA".equalsIgnoreCase(algorithm)) throw new TNoSuchAlgorithmException(algorithm);
        return new TSignature();
    }

    public final void initVerify(TPublicKey publicKey) throws TInvalidKeyException {
        if (!(publicKey instanceof TRsaPublicKey)) throw new TInvalidKeyException("RSA key expected");
        key = (TRsaPublicKey) publicKey;
        digest.reset();
    }

    public final void update(byte[] data) throws TSignatureException { digest.update(data); }
    public final void update(byte[] data, int off, int len) throws TSignatureException { digest.update(data, off, len); }
    public final void update(byte b) throws TSignatureException { digest.update(b); }

    public final boolean verify(byte[] signature) throws TSignatureException {
        if (key == null) throw new TSignatureException("not initialized");
        byte[] hash = digest.digest();
        int k = (key.modulus.bitLength() + 7) / 8;
        if (signature.length != k) return false;
        java.math.BigInteger s = new java.math.BigInteger(1, signature);
        if (s.compareTo(key.modulus) >= 0) return false;
        byte[] em = s.modPow(key.exponent, key.modulus).toByteArray();
        byte[] full = new byte[k];
        int copy = Math.min(em.length, k);
        System.arraycopy(em, em.length - copy, full, k - copy, copy);
        // 0x00 0x01 FF..FF 0x00 DigestInfo
        int tLen = SHA256_PREFIX.length + hash.length;
        if (k < tLen + 11 || full[0] != 0 || full[1] != 1) return false;
        int ps = k - tLen - 3;
        for (int i = 0; i < ps; i++) if (full[2 + i] != (byte) 0xFF) return false;
        if (full[2 + ps] != 0) return false;
        int p = 3 + ps;
        for (int i = 0; i < SHA256_PREFIX.length; i++) if (full[p + i] != SHA256_PREFIX[i]) return false;
        p += SHA256_PREFIX.length;
        int diff = 0;
        for (int i = 0; i < hash.length; i++) diff |= full[p + i] ^ hash[i];
        return diff == 0;
    }
}
