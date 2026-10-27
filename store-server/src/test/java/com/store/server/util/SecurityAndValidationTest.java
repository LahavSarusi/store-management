package com.store.server.util;

import com.store.server.model.PasswordPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SecurityAndValidationTest {

    @Test
    void hashedPasswordVerifiesAndIsSalted() {
        String h1 = PasswordHasher.hash("Secret123");
        String h2 = PasswordHasher.hash("Secret123");
        assertNotEquals(h1, h2, "each hash must use a fresh salt");
        assertTrue(PasswordHasher.verify("Secret123", h1));
        assertFalse(PasswordHasher.verify("secret123", h1));
        assertFalse(PasswordHasher.verify("Secret123", "garbage"));
        assertFalse(PasswordHasher.verify(null, h1));
    }

    @Test
    void israeliIdChecksum() {
        assertTrue(Validation.isValidIsraeliId("123456782"));
        assertTrue(Validation.isValidIsraeliId("314159260"));
        assertFalse(Validation.isValidIsraeliId("123456789"));
        assertFalse(Validation.isValidIsraeliId("12a456782"));
        assertFalse(Validation.isValidIsraeliId(null));
    }

    @Test
    void phoneFormat() {
        assertTrue(Validation.isValidPhone("050-1234567"));
        assertTrue(Validation.isValidPhone("0501234567"));
        assertTrue(Validation.isValidPhone("03-1234567"));
        assertFalse(Validation.isValidPhone("12345"));
        assertFalse(Validation.isValidPhone(null));
    }

    @Test
    void passwordPolicyListsEveryViolation() {
        PasswordPolicy p = new PasswordPolicy(8, true, true, true, 0);
        assertEquals(4, p.violations("abc").size());
        assertTrue(p.violations("Abcdefg1!").isEmpty());
        assertFalse(p.violations(null).isEmpty());
    }

    @Test
    void passwordExpiry() {
        PasswordPolicy p = new PasswordPolicy(4, false, false, false, 30);
        long now = System.currentTimeMillis();
        assertFalse(p.isExpired(now - 29L * 86_400_000L, now));
        assertTrue(p.isExpired(now - 31L * 86_400_000L, now));
        assertFalse(new PasswordPolicy(4, false, false, false, 0).isExpired(0, now), "0 = never expires");
    }
}
