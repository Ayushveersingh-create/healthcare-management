package com.sehatsetu.util;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JwtUtil Unit Tests")
class JwtUtilTest {

    @Test
    @DisplayName("Should generate and parse a valid JWT token")
    void testGenerateAndParseToken() {
        Long userId = 101L;
        String email = "test.patient@example.in";
        String role = "PATIENT";

        String token = JwtUtil.generateToken(userId, email, role);
        assertNotNull(token);
        assertFalse(token.isEmpty());

        Claims claims = JwtUtil.parseToken(token);
        assertNotNull(claims);
        assertEquals(userId, claims.get("user_id", Long.class));
        assertEquals(email, claims.get("email", String.class));
        assertEquals(role, claims.get("role", String.class));
        assertEquals(email, claims.getSubject());
    }

    @Test
    @DisplayName("Should parse token prefixed with 'Bearer '")
    void testParseTokenWithBearerPrefix() {
        Long userId = 202L;
        String email = "doctor@example.in";
        String role = "DOCTOR";

        String rawToken = JwtUtil.generateToken(userId, email, role);
        String bearerToken = "Bearer " + rawToken;

        Claims claims = JwtUtil.parseToken(bearerToken);
        assertNotNull(claims);
        assertEquals(userId, claims.get("user_id", Long.class));
        assertEquals(role, claims.get("role", String.class));
    }

    @Test
    @DisplayName("Should return null for invalid, malformed, or null tokens")
    void testInvalidTokensReturnNull() {
        assertNull(JwtUtil.parseToken(null));
        assertNull(JwtUtil.parseToken(""));
        assertNull(JwtUtil.parseToken("Bearer invalid.token.structure"));
        assertNull(JwtUtil.parseToken("random_string_12345"));
    }
}
