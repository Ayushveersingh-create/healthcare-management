package com.sehatsetu.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PasswordUtil Unit Tests")
class PasswordUtilTest {

    @Test
    @DisplayName("Should hash and verify plain text password correctly")
    void testHashAndVerifyPassword() {
        String plainPassword = "SecurePassword@123";
        String hash = PasswordUtil.hashPassword(plainPassword);

        assertNotNull(hash);
        assertTrue(hash.startsWith("$2a$") || hash.startsWith("$2b$") || hash.startsWith("$2y$"));

        assertTrue(PasswordUtil.verifyPassword(plainPassword, hash));
        assertFalse(PasswordUtil.verifyPassword("WrongPassword@123", hash));
    }

    @Test
    @DisplayName("Should return false for null or empty inputs")
    void testVerifyPasswordEdgeCases() {
        String hash = PasswordUtil.hashPassword("ValidPassword123");

        assertFalse(PasswordUtil.verifyPassword(null, hash));
        assertFalse(PasswordUtil.verifyPassword("ValidPassword123", null));
        assertFalse(PasswordUtil.verifyPassword("ValidPassword123", ""));
        assertFalse(PasswordUtil.verifyPassword("ValidPassword123", "not_a_bcrypt_hash"));
    }
}
