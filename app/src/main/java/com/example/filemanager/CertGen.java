package com.example.filemanager;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.security.Signature;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

/** Builds a minimal self-signed X.509 v3 certificate (DER) with no external libraries. */
public final class CertGen {
    private CertGen() {}

    private static byte[] cat(byte[]... parts) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        for (byte[] p : parts) o.write(p, 0, p.length);
        return o.toByteArray();
    }

    private static byte[] tlv(int tag, byte[]... parts) {
        byte[] body = cat(parts);
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write(tag);
        int n = body.length;
        if (n < 128) o.write(n);
        else if (n < 256) { o.write(0x81); o.write(n); }
        else if (n < 65536) { o.write(0x82); o.write(n >> 8); o.write(n & 0xFF); }
        else { o.write(0x83); o.write(n >> 16); o.write((n >> 8) & 0xFF); o.write(n & 0xFF); }
        o.write(body, 0, body.length);
        return o.toByteArray();
    }

    private static byte[] time(Date d) {
        SimpleDateFormat utc = new SimpleDateFormat("yyMMddHHmmss'Z'");
        SimpleDateFormat gen = new SimpleDateFormat("yyyyMMddHHmmss'Z'");
        utc.setTimeZone(TimeZone.getTimeZone("UTC"));
        gen.setTimeZone(TimeZone.getTimeZone("UTC"));
        int year = Integer.parseInt(new SimpleDateFormat("yyyy").format(d));
        return year < 2050
                ? tlv(0x17, utc.format(d).getBytes(StandardCharsets.US_ASCII))
                : tlv(0x18, gen.format(d).getBytes(StandardCharsets.US_ASCII));
    }

    /** @return DER encoded certificate, valid ~25 years, signed SHA256withRSA. */
    public static byte[] selfSigned(KeyPair kp, String cn) throws Exception {
        byte[] oidSha256Rsa = {0x2A, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xF7, 0x0D, 0x01, 0x01, 0x0B};
        byte[] oidCn = {0x55, 0x04, 0x03};
        byte[] sigAlg = tlv(0x30, tlv(0x06, oidSha256Rsa), new byte[]{0x05, 0x00});
        byte[] name = tlv(0x30, tlv(0x31, tlv(0x30, tlv(0x06, oidCn), tlv(0x0C, cn.getBytes(StandardCharsets.UTF_8)))));
        long now = System.currentTimeMillis();
        byte[] validity = tlv(0x30, time(new Date(now - 86400000L)), time(new Date(now + 25L * 365 * 86400000L)));
        byte[] version = tlv(0xA0, tlv(0x02, new byte[]{2}));
        byte[] serial = tlv(0x02, new BigInteger(63, new SecureRandom()).add(BigInteger.ONE).toByteArray());
        byte[] tbs = tlv(0x30, version, serial, sigAlg, name, validity, name, kp.getPublic().getEncoded());
        Signature s = Signature.getInstance("SHA256withRSA");
        s.initSign(kp.getPrivate());
        s.update(tbs);
        byte[] sig = s.sign();
        return tlv(0x30, tbs, sigAlg, tlv(0x03, new byte[]{0}, sig));
    }
}
