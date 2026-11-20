package com.hospital.v2.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SensitiveDataCryptoTest {
    private final SensitiveDataCrypto crypto = new SensitiveDataCrypto(
            "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");

    @Test
    void encrypt_is_random_and_round_trips() {
        String first = crypto.encrypt("13800138000");
        String second = crypto.encrypt("13800138000");
        assertTrue(first.startsWith("v1:"));
        assertFalse(first.contains("13800138000"));
        assertFalse(first.equals(second));
        assertEquals("13800138000", crypto.decrypt(first));
        assertEquals("138****8000", crypto.maskPhone(first));
    }

    @Test
    void legacy_plaintext_can_be_read_until_migration() {
        assertEquals("138****8000", crypto.maskPhone("13800138000"));
        assertEquals("f42e1f9dfd9a95374bfc856a930faa1b7a083fc72c3a1509c231c45566c804ca",
                crypto.sha256("not-a-real-id"));
    }
}
