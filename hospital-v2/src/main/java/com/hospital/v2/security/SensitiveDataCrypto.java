package com.hospital.v2.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM for sensitive patient fields.
 *
 * The v1 prefix makes encrypted values distinguishable from legacy plaintext,
 * so an existing volume can be migrated idempotently. The encryption key must
 * be injected separately in the mysql profile and must never be committed.
 */
@Component
public final class SensitiveDataCrypto {
    private static final String PREFIX = "v1:";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final byte[] key;
    private final SecureRandom random = new SecureRandom();

    public SensitiveDataCrypto(@Value("${hospital.crypto.aes-key}") String encodedKey) {
        try {
            byte[] decoded = null;
            try {
                decoded = Base64.getDecoder().decode(encodedKey);
            } catch (IllegalArgumentException ignored) {
                // Existing installations may provide a raw JWT secret.
            }
            if (decoded != null && decoded.length == 32) {
                this.key = decoded;
            } else if (encodedKey != null && encodedKey.getBytes(StandardCharsets.UTF_8).length >= 32) {
                // Backward-compatible bridge for existing servers that only
                // have AUTH_JWT_SECRET. Provision CARENEXUS_AES_KEY separately
                // before treating the deployment as fully production-hardened.
                this.key = MessageDigest.getInstance("SHA-256").digest(encodedKey.getBytes(StandardCharsets.UTF_8));
            } else {
                throw new IllegalStateException("hospital.crypto.aes-key 必须是 Base64 32 字节密钥，或至少 32 字符的旧 JWT 密钥");
            }
        } catch (Exception e) {
            throw new IllegalStateException("hospital.crypto.aes-key 初始化失败", e);
        }
    }

    public String encrypt(String value) {
        if (value == null || value.isBlank() || isEncrypted(value)) return value;
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
            byte[] payload = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] packed = new byte[iv.length + payload.length];
            System.arraycopy(iv, 0, packed, 0, iv.length);
            System.arraycopy(payload, 0, packed, iv.length, payload.length);
            return PREFIX + Base64.getEncoder().encodeToString(packed);
        } catch (Exception e) {
            throw new IllegalStateException("敏感字段加密失败", e);
        }
    }

    public String decrypt(String value) {
        if (value == null || value.isBlank() || !isEncrypted(value)) return value;
        try {
            byte[] packed = Base64.getDecoder().decode(value.substring(PREFIX.length()));
            if (packed.length <= IV_LENGTH) throw new IllegalArgumentException("密文长度错误");
            byte[] iv = java.util.Arrays.copyOfRange(packed, 0, IV_LENGTH);
            byte[] payload = java.util.Arrays.copyOfRange(packed, IV_LENGTH, packed.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(payload), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("敏感字段解密失败，请检查 CARENEXUS_AES_KEY", e);
        }
    }

    /** SHA-256 is only for exact-match lookup; it must not be used to display the value. */
    public String sha256(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.trim().getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(digest.length * 2);
            for (byte b : digest) out.append(String.format("%02x", b));
            return out.toString();
        } catch (Exception e) {
            throw new IllegalStateException("敏感字段哈希失败", e);
        }
    }

    public String maskPhone(String value) {
        String plain = decrypt(value);
        if (plain == null || plain.isBlank()) return plain;
        if (plain.length() < 7) return "******";
        return plain.substring(0, 3) + "****" + plain.substring(plain.length() - 4);
    }

    public String maskIdCard(String value) {
        String plain = decrypt(value);
        if (plain == null || plain.isBlank()) return plain;
        if (plain.length() < 8) return "********";
        return plain.substring(0, 4) + "**********" + plain.substring(plain.length() - 4);
    }

    public boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }
}
