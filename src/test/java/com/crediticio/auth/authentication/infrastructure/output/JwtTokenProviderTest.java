package com.crediticio.auth.authentication.infrastructure.output;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    private JwtTokenProvider tokenProvider;

    private static final String SECRET = "test-secret-key-for-testing-purposes-only-minimum-256-bits-long!!";
    private static final long EXPIRATION_MS = 3600000L;

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(tokenProvider, "jwtSecret", SECRET);
        ReflectionTestUtils.setField(tokenProvider, "jwtExpirationMs", EXPIRATION_MS);
    }

    @Test
    void generateToken_createsValidToken() {
        String token = tokenProvider.generateToken("juan@test.com", "ADMINISTRADOR_RIESGO");

        assertNotNull(token);
        assertTrue(tokenProvider.isTokenValid(token));
    }

    @Test
    void getEmailFromToken_returnsCorrectEmail() {
        String token = tokenProvider.generateToken("juan@test.com", "ADMINISTRADOR_RIESGO");

        assertEquals("juan@test.com", tokenProvider.getEmailFromToken(token));
    }

    @Test
    void getRolFromToken_returnsCorrectRol() {
        String token = tokenProvider.generateToken("juan@test.com", "ANALISTA_CREDITO");

        assertEquals("ANALISTA_CREDITO", tokenProvider.getRolFromToken(token));
    }

    @Test
    void isTokenValid_expiredToken_returnsFalse() {
        String expiredToken = Jwts.builder()
                .subject("juan@test.com")
                .claim("rol", "ADMINISTRADOR_RIESGO")
                .issuedAt(new Date(System.currentTimeMillis() - 10000))
                .expiration(new Date(System.currentTimeMillis() - 5000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertFalse(tokenProvider.isTokenValid(expiredToken));
    }

    @Test
    void isTokenValid_malformedToken_returnsFalse() {
        assertFalse(tokenProvider.isTokenValid("not.a.valid.token"));
    }

    @Test
    void isTokenValid_nullToken_returnsFalse() {
        assertFalse(tokenProvider.isTokenValid(null));
    }

    @Test
    void isTokenValid_wrongSecret_returnsFalse() {
        String tokenWithDifferentKey = Jwts.builder()
                .subject("juan@test.com")
                .claim("rol", "ADMINISTRADOR_RIESGO")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(Keys.hmacShaKeyFor("another-secret-key-that-is-at-least-256-bits-long-for-hmac!!".getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertFalse(tokenProvider.isTokenValid(tokenWithDifferentKey));
    }
}
