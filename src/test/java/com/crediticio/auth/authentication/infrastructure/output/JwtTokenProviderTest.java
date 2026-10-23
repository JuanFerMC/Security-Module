package com.crediticio.auth.authentication.infrastructure.output;

import com.crediticio.auth.TestRsaKeyInitializer;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.InputStream;
import java.nio.file.Path;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    private JwtTokenProvider tokenProvider;

    private static final String ISSUER = "crediticio-auth";
    private static final List<String> AUDIENCES = List.of("scoring-api", "evaluation-query-api");
    private static RSAPrivateKey testPrivateKey;
    private static RSAPublicKey testPublicKey;
    private static Path keyDir;

    @BeforeAll
    static void generateKeys() throws Exception {
        TestRsaKeyInitializer.ensureKeys();
        keyDir = TestRsaKeyInitializer.getTempDir();
        try (InputStream in = new FileSystemResource(keyDir.resolve("jwt-privada-test.pem")).getInputStream()) {
            testPrivateKey = RsaKeyConverters.pkcs8().convert(in);
        }
        try (InputStream in = new FileSystemResource(keyDir.resolve("jwt-publica-test.pem")).getInputStream()) {
            testPublicKey = RsaKeyConverters.x509().convert(in);
        }
    }

    @BeforeEach
    void setUp() {
        tokenProvider = provider(900000L, 7L);
    }

    private JwtTokenProvider provider(long accessMs, long refreshDays) {
        JwtTokenProvider p = new JwtTokenProvider();
        ReflectionTestUtils.setField(p, "privateKeyResource",
                new FileSystemResource(keyDir.resolve("jwt-privada-test.pem")));
        ReflectionTestUtils.setField(p, "publicKeyResource",
                new FileSystemResource(keyDir.resolve("jwt-publica-test.pem")));
        ReflectionTestUtils.setField(p, "accessExpirationMs", accessMs);
        ReflectionTestUtils.setField(p, "refreshExpirationDays", refreshDays);
        ReflectionTestUtils.setField(p, "issuer", ISSUER);
        ReflectionTestUtils.setField(p, "audiences", AUDIENCES);
        p.validateConfiguration();
        return p;
    }

    private String forgedToken(String issuer, long expiresInMs) {
        return Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .issuer(issuer)
                .audience().add("scoring-api").and()
                .claim("role", "ADMINISTRADOR_RIESGO")
                .issuedAt(new Date(System.currentTimeMillis() - 10000))
                .expiration(new Date(System.currentTimeMillis() + expiresInMs))
                .signWith(testPrivateKey, Jwts.SIG.RS256)
                .compact();
    }

    @Test
    void generateToken_createsValidToken() {
        UUID uuid = UUID.randomUUID();
        String token = tokenProvider.generateToken(uuid, "juan@test.com", "ADMINISTRADOR_RIESGO");

        assertNotNull(token);
        assertTrue(tokenProvider.isTokenValid(token));
    }

    @Test
    void generateToken_includesCorrectClaims() {
        UUID uuid = UUID.randomUUID();
        String token = tokenProvider.generateToken(uuid, "juan@test.com", "ANALISTA_CREDITO");

        Claims claims = Jwts.parser()
                .verifyWith(testPublicKey)
                .build().parseSignedClaims(token).getPayload();

        assertEquals(uuid.toString(), claims.getSubject());
        assertEquals(ISSUER, claims.getIssuer());
        assertTrue(claims.getAudience().contains("scoring-api"));
        assertTrue(claims.getAudience().contains("evaluation-query-api"));
        assertEquals("ANALISTA_CREDITO", claims.get("role", String.class));
        assertNotNull(claims.getIssuedAt());
        assertNotNull(claims.getId());
        assertEquals(15 * 60 * 1000L, claims.getExpiration().getTime() - claims.getIssuedAt().getTime());
        assertEquals(900L, tokenProvider.getAccessExpirationSeconds());
    }

    @Test
    void getters_returnCorrectClaims() {
        UUID uuid = UUID.randomUUID();
        String token = tokenProvider.generateToken(uuid, "juan@test.com", "ANALISTA_CREDITO");

        assertEquals(uuid, tokenProvider.getUuidFromToken(token));
        assertEquals("juan@test.com", tokenProvider.getEmailFromToken(token));
        assertEquals("ANALISTA_CREDITO", tokenProvider.getRolFromToken(token));
    }

    @Test
    void isTokenValid_expiredToken_returnsFalse() {
        assertFalse(tokenProvider.isTokenValid(forgedToken(ISSUER, -5000)));
    }

    @Test
    void isTokenValid_wrongIssuer_returnsFalse() {
        assertFalse(tokenProvider.isTokenValid(forgedToken("otro-emisor", 60000)));
    }

    @Test
    void isTokenValid_tamperedToken_returnsFalse() {
        UUID uuid = UUID.randomUUID();
        String token = tokenProvider.generateToken(uuid, "juan@test.com", "ANALISTA_CREDITO");
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
    void isTokenValid_hmacToken_isRejected() {
        String hmacToken = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .issuer(ISSUER)
                .audience().add("scoring-api").and()
                .claim("role", "ADMINISTRADOR_RIESGO")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                        "test-secret-key-for-testing-purposes-only-minimum-256-bits-long!!"
                                .getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .compact();

        assertFalse(tokenProvider.isTokenValid(hmacToken));
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
        assertDoesNotThrow(() -> provider(900000L, 7L));
    }

    @Test
    void validateConfiguration_rejectsLifetimesAboveLimits() {
        assertThrows(IllegalStateException.class, () -> provider(900001L, 7L));
        assertThrows(IllegalStateException.class, () -> provider(900000L, 8L));
    }
}
