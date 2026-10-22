package com.crediticio.auth.authentication.infrastructure.output;

import com.crediticio.auth.authentication.ports.output.TokenProviderPort;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Component
public class JwtTokenProvider implements TokenProviderPort {

    static final long MAX_ACCESS_EXPIRATION_MS = 15L * 60 * 1000;
    static final long MAX_REFRESH_EXPIRATION_DAYS = 7;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Value("${app.jwt.private-key}")
    private Resource privateKeyResource;

    @Value("${app.jwt.public-key}")
    private Resource publicKeyResource;

    @Value("${app.jwt.access-expiration-ms}")
    private long accessExpirationMs;

    @Value("${app.jwt.refresh-expiration-days}")
    private long refreshExpirationDays;

    @Value("${app.jwt.issuer}")
    private String issuer;

    @Value("${app.jwt.audiences}")
    private List<String> audiences;

    private RSAPrivateKey privateKey;
    private RSAPublicKey publicKey;

    @PostConstruct
    void validateConfiguration() {
        if (privateKeyResource == null || !privateKeyResource.exists()) {
            throw new IllegalStateException("La clave privada RSA es obligatoria (app.jwt.private-key)");
        }
        if (publicKeyResource == null || !publicKeyResource.exists()) {
            throw new IllegalStateException("La clave pública RSA es obligatoria (app.jwt.public-key)");
        }
        this.privateKey = leerClavePrivada(privateKeyResource);
        this.publicKey = leerClavePublica(publicKeyResource);
        if (accessExpirationMs <= 0 || accessExpirationMs > MAX_ACCESS_EXPIRATION_MS) {
            throw new IllegalStateException(
                    "JWT_EXPIRATION_MS debe estar entre 1 y " + MAX_ACCESS_EXPIRATION_MS + " (15 minutos)");
        }
        if (refreshExpirationDays <= 0 || refreshExpirationDays > MAX_REFRESH_EXPIRATION_DAYS) {
            throw new IllegalStateException(
                    "JWT_REFRESH_EXPIRATION_DAYS debe estar entre 1 y " + MAX_REFRESH_EXPIRATION_DAYS);
        }
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalStateException("JWT_ISSUER no puede estar vacío");
        }
        if (audiences == null || audiences.isEmpty()) {
            throw new IllegalStateException("JWT_AUDIENCES no puede estar vacío");
        }
    }

    @Override
    public String generateToken(UUID uuidPublico, String email, String rol) {
        Date now = new Date();
        var builder = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(uuidPublico.toString())
                .issuer(issuer)
                .claim("email", email)
                .claim("role", rol)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessExpirationMs))
                .signWith(privateKey, Jwts.SIG.RS256);
        for (String aud : audiences) {
            builder.audience().add(aud);
        }
        return builder.compact();
    }

    @Override
    public UUID getUuidFromToken(String token) {
        return UUID.fromString(getClaims(token).getSubject());
    }

    @Override
    public String getEmailFromToken(String token) {
        return getClaims(token).get("email", String.class);
    }

    @Override
    public String getRolFromToken(String token) {
        return getClaims(token).get("role", String.class);
    }

    @Override
    public boolean isTokenValid(String token) {
        try {
            UUID.fromString(getClaims(token).getSubject());
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
                .verifyWith(publicKey)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private static RSAPrivateKey leerClavePrivada(Resource recurso) {
        try (InputStream entrada = recurso.getInputStream()) {
            return RsaKeyConverters.pkcs8().convert(entrada);
        } catch (IOException ex) {
            throw new UncheckedIOException("No fue posible leer la clave privada RSA", ex);
        }
    }

    private static RSAPublicKey leerClavePublica(Resource recurso) {
        try (InputStream entrada = recurso.getInputStream()) {
            return RsaKeyConverters.x509().convert(entrada);
        } catch (IOException ex) {
            throw new UncheckedIOException("No fue posible leer la clave pública RSA", ex);
        }
    }
}
