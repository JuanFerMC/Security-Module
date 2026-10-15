package com.crediticio.auth.authentication.infrastructure.output;

import com.crediticio.auth.authentication.ports.output.TokenProviderPort;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.UUID;

@Component
public class JwtTokenProvider implements TokenProviderPort {

    /** HU09 RNF02: el access token no puede superar 15 minutos. */
    static final long MAX_ACCESS_EXPIRATION_MS = 15L * 60 * 1000;
    /** HU09 RF06/RNF02: el refresh token dura como maximo 7 dias. */
    static final long MAX_REFRESH_EXPIRATION_DAYS = 7;
    /** HS256 requiere al menos 256 bits. */
    static final int MIN_SECRET_BYTES = 32;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.access-expiration-ms}")
    private long accessExpirationMs;

    @Value("${app.jwt.refresh-expiration-days}")
    private long refreshExpirationDays;

    @Value("${app.jwt.issuer}")
    private String issuer;

    @Value("${app.jwt.audience}")
    private String audience;

    /** Falla el arranque si la configuracion criptografica o de vigencia es insegura (HU09 RNF01/RNF02). */
    @PostConstruct
    void validateConfiguration() {
        if (jwtSecret == null || jwtSecret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET es obligatoria y debe tener al menos " + MIN_SECRET_BYTES + " caracteres (256 bits)");
        }
        if (accessExpirationMs <= 0 || accessExpirationMs > MAX_ACCESS_EXPIRATION_MS) {
            throw new IllegalStateException(
                    "JWT_EXPIRATION_MS debe estar entre 1 y " + MAX_ACCESS_EXPIRATION_MS + " (15 minutos)");
        }
        if (refreshExpirationDays <= 0 || refreshExpirationDays > MAX_REFRESH_EXPIRATION_DAYS) {
            throw new IllegalStateException(
                    "JWT_REFRESH_EXPIRATION_DAYS debe estar entre 1 y " + MAX_REFRESH_EXPIRATION_DAYS);
        }
        if (issuer == null || issuer.isBlank() || audience == null || audience.isBlank()) {
            throw new IllegalStateException("JWT_ISSUER y JWT_AUDIENCE no pueden estar vacios");
        }
    }

    @Override
    public String generateToken(Long userId, String email, String rol) {
        Date now = new Date();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(userId))
                .issuer(issuer)
                .audience().add(audience).and()
                .claim("email", email)
                .claim("rol", rol)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessExpirationMs))
                .signWith(getSigningKey())
                .compact();
    }

    @Override
    public Long getUserIdFromToken(String token) {
        return Long.valueOf(getClaims(token).getSubject());
    }

    @Override
    public String getEmailFromToken(String token) {
        return getClaims(token).get("email", String.class);
    }

    @Override
    public String getRolFromToken(String token) {
        return getClaims(token).get("rol", String.class);
    }

    @Override
    public boolean isTokenValid(String token) {
        try {
            Long.valueOf(getClaims(token).getSubject());
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    @Override
    public String generateRefreshToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @Override
    public String hashRefreshToken(String refreshToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(refreshToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    @Override
    public long getAccessExpirationSeconds() {
        return accessExpirationMs / 1000;
    }

    @Override
    public long getRefreshExpirationDays() {
        return refreshExpirationDays;
    }

    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .requireIssuer(issuer)
                .requireAudience(audience)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }
}
