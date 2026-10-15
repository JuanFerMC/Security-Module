package com.crediticio.auth.authentication.infrastructure.output;

import io.jsonwebtoken.Claims;
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
    private static final String ISSUER = "crediticio-auth";
    private static final String AUDIENCE = "crediticio-scoring";

    @BeforeEach
    void setUp() {
        tokenProvider = provider(SECRET, 900000L, 7L);
    }

    private JwtTokenProvider provider(String secret, long accessMs, long refreshDays) {
        JwtTokenProvider p = new JwtTokenProvider();
        ReflectionTestUtils.setField(p, "jwtSecret", secret);
        ReflectionTestUtils.setField(p, "accessExpirationMs", accessMs);
        ReflectionTestUtils.setField(p, "refreshExpirationDays", refreshDays);
        ReflectionTestUtils.setField(p, "issuer", ISSUER);
        ReflectionTestUtils.setField(p, "audience", AUDIENCE);
        return p;
    }

    private String tokenWith(String issuer, String audience, long expiresInMs, String secret) {
        return Jwts.builder()
                .subject("7")
                .issuer(issuer)
                .audience().add(audience).and()
                .claim("rol", "ADMINISTRADOR_RIESGO")
                .issuedAt(new Date(System.currentTimeMillis() - 10000))
                .expiration(new Date(System.currentTimeMillis() + expiresInMs))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    @Test
    void generateToken_createsValidToken() {
        String token = tokenProvider.generateToken(7L, "juan@test.com", "ADMINISTRADOR_RIESGO");

        assertNotNull(token);
        assertTrue(tokenProvider.isTokenValid(token));
    }

    @Test
    void generateToken_includesIdentityIssuerAudienceAndFifteenMinutes() {
        String token = tokenProvider.generateToken(7L, "juan@test.com", "ANALISTA_CREDITO");

        Claims claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .build().parseSignedClaims(token).getPayload();

        assertEquals("7", claims.getSubject());
        assertEquals(ISSUER, claims.getIssuer());
        assertTrue(claims.getAudience().contains(AUDIENCE));
        assertEquals("ANALISTA_CREDITO", claims.get("rol", String.class));
        assertNotNull(claims.getIssuedAt());
        assertNotNull(claims.getId());
        assertEquals(15 * 60 * 1000L, claims.getExpiration().getTime() - claims.getIssuedAt().getTime());
        assertEquals(900L, tokenProvider.getAccessExpirationSeconds());
    }

    @Test
    void getters_returnCorrectClaims() {
        String token = tokenProvider.generateToken(7L, "juan@test.com", "ANALISTA_CREDITO");

        assertEquals(7L, tokenProvider.getUserIdFromToken(token));
        assertEquals("juan@test.com", tokenProvider.getEmailFromToken(token));
        assertEquals("ANALISTA_CREDITO", tokenProvider.getRolFromToken(token));
    }

    @Test
    void isTokenValid_expiredToken_returnsFalse() {
        assertFalse(tokenProvider.isTokenValid(tokenWith(ISSUER, AUDIENCE, -5000, SECRET)));
    }

    @Test
    void isTokenValid_wrongIssuer_returnsFalse() {
        assertFalse(tokenProvider.isTokenValid(tokenWith("otro-emisor", AUDIENCE, 60000, SECRET)));
    }

    @Test
    void isTokenValid_wrongAudience_returnsFalse() {
        assertFalse(tokenProvider.isTokenValid(tokenWith(ISSUER, "otra-audiencia", 60000, SECRET)));
    }

    @Test
    void isTokenValid_tamperedToken_returnsFalse() {
        String token = tokenProvider.generateToken(7L, "juan@test.com", "ANALISTA_CREDITO");
        String[] parts = token.split("\\.");
        String tampered = parts[0] + "." + parts[1] + "A." + parts[2];

        assertFalse(tokenProvider.isTokenValid(tampered));
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
        String other = tokenWith(ISSUER, AUDIENCE, 60000, "another-secret-key-that-is-at-least-256-bits-long-for-hmac!!");

        assertFalse(tokenProvider.isTokenValid(other));
    }

    @Test
    void refreshToken_isOpaqueUniqueAndHashed() {
        String a = tokenProvider.generateRefreshToken();
        String b = tokenProvider.generateRefreshToken();

        assertNotEquals(a, b);
        assertTrue(a.length() >= 43);
        String hash = tokenProvider.hashRefreshToken(a);
        assertEquals(64, hash.length());
        assertNotEquals(a, hash);
        assertEquals(hash, tokenProvider.hashRefreshToken(a));
    }

    @Test
    void validateConfiguration_acceptsLimits() {
        assertDoesNotThrow(() -> provider(SECRET, 900000L, 7L).validateConfiguration());
    }

    @Test
    void validateConfiguration_rejectsShortSecret() {
        assertThrows(IllegalStateException.class, () -> provider("corta", 900000L, 7L).validateConfiguration());
        assertThrows(IllegalStateException.class, () -> provider("", 900000L, 7L).validateConfiguration());
    }

    @Test
    void validateConfiguration_rejectsLifetimesAboveLimits() {
        assertThrows(IllegalStateException.class, () -> provider(SECRET, 900001L, 7L).validateConfiguration());
        assertThrows(IllegalStateException.class, () -> provider(SECRET, 900000L, 8L).validateConfiguration());
    }
}
