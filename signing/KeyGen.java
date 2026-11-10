import java.io.FileOutputStream;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Date;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

/**
 * Re-creates the same app signing key on every CI run from the FITZTV_SIGNING_PASSWORD
 * repository secret, so each build can update the installed app. The key never lives in
 * the repository: without the secret it cannot be reproduced.
 *
 * Usage: java -cp <bouncycastle jars> KeyGen.java <password> <out.p12>
 */
public class KeyGen {
    private static byte[] seed;
    private static long counter = 0;

    private static byte[] block() throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(seed);
        md.update(BigInteger.valueOf(counter++).toByteArray());
        return md.digest();
    }

    private static BigInteger prime(int bits) throws Exception {
        byte[] b = new byte[bits / 8];
        int i = 0;
        while (i < b.length) {
            byte[] x = block();
            int n = Math.min(x.length, b.length - i);
            System.arraycopy(x, 0, b, i, n);
            i += n;
        }
        b[0] |= (byte) 0xC0;           // full bit length
        b[b.length - 1] |= 1;          // odd
        return new BigInteger(1, b).nextProbablePrime();
    }

    public static void main(String[] args) throws Exception {
        String pass = args[0];
        String out = args[1];
        // Stretch the password so it can't be brute-forced quickly.
        byte[] s = ("fitztv-signing-v1:" + pass).getBytes("UTF-8");
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        for (int i = 0; i < 200_000; i++) s = md.digest(s);
        seed = s;

        BigInteger e = BigInteger.valueOf(65537);
        BigInteger p, q, phi;
        while (true) {
            p = prime(1536);
            q = prime(1536);
            if (p.equals(q)) continue;
            phi = p.subtract(BigInteger.ONE).multiply(q.subtract(BigInteger.ONE));
            if (phi.gcd(e).equals(BigInteger.ONE)) break;
        }
        BigInteger n = p.multiply(q);
        BigInteger d = e.modInverse(phi);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        PrivateKey priv = kf.generatePrivate(new RSAPrivateCrtKeySpec(
            n, e, d, p, q, d.mod(p.subtract(BigInteger.ONE)), d.mod(q.subtract(BigInteger.ONE)), q.modInverse(p)));
        PublicKey pub = kf.generatePublic(new RSAPublicKeySpec(n, e));

        // Fixed name, serial and dates so the certificate is byte-for-byte identical every time.
        X500Name name = new X500Name("CN=Fitz TV, O=Fitz, C=AU");
        Date from = new Date(1767225600000L);   // 2026-01-01
        Date to = new Date(4922899200000L);     // 2126-01-01
        X509Certificate cert = new JcaX509CertificateConverter().getCertificate(
            new JcaX509v3CertificateBuilder(name, BigInteger.ONE, from, to, name, pub)
                .build(new JcaContentSignerBuilder("SHA256withRSA").build(priv)));

        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, null);
        ks.setKeyEntry("fitztv", priv, pass.toCharArray(), new Certificate[]{cert});
        try (FileOutputStream f = new FileOutputStream(out)) { ks.store(f, pass.toCharArray()); }
        System.out.println("Signing key ready");
    }
}
