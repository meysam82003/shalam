package org.teavm.classlib.java.security;

/** RSA public key parsed from X.509 SubjectPublicKeyInfo (modulus and exponent). */
public final class TRsaPublicKey implements TPublicKey {
    final java.math.BigInteger modulus;
    final java.math.BigInteger exponent;
    private final byte[] encoded;

    TRsaPublicKey(byte[] der) throws TGeneralSecurityException {
        encoded = der.clone();
        try {
            Der spki = new Der(der, 0);
            Der seq = spki.child(0);              // SEQUENCE
            int p = seq.contentStart;
            Der alg = new Der(der, p);            // AlgorithmIdentifier
            p = alg.end;
            Der bits = new Der(der, p);           // BIT STRING
            int inner = bits.contentStart + 1;    // skip unused-bits byte
            Der rsa = new Der(der, inner);        // SEQUENCE { modulus, exponent }
            Der n = new Der(der, rsa.contentStart);
            Der e = new Der(der, n.end);
            modulus = new java.math.BigInteger(1, java.util.Arrays.copyOfRange(der, n.contentStart, n.end));
            exponent = new java.math.BigInteger(1, java.util.Arrays.copyOfRange(der, e.contentStart, e.end));
        } catch (RuntimeException ex) {
            throw new TGeneralSecurityException("bad RSA key");
        }
    }

    @Override public String getAlgorithm() { return "RSA"; }
    @Override public String getFormat() { return "X.509"; }
    @Override public byte[] getEncoded() { return encoded.clone(); }

    /** Minimal DER reader: tag, length and content bounds. */
    static final class Der {
        final int tag, contentStart, end;

        Der(byte[] d, int pos) {
            tag = d[pos] & 0xFF;
            int len = d[pos + 1] & 0xFF;
            int start = pos + 2;
            if ((len & 0x80) != 0) {
                int n = len & 0x7F;
                len = 0;
                for (int i = 0; i < n; i++) len = (len << 8) | (d[pos + 2 + i] & 0xFF);
                start = pos + 2 + n;
            }
            contentStart = start;
            end = start + len;
        }

        Der child(int unused) { return this; }
    }
}
