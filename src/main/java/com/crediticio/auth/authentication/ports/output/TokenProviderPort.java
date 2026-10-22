package com.crediticio.auth.authentication.ports.output;

import java.util.UUID;

public interface TokenProviderPort {

    /** Access token JWT RS256: sub = UUID público del usuario, con emisor, audiencias y vigencia corta. */
    String generateToken(UUID uuidPublico, String email, String rol);

    UUID getUuidFromToken(String token);

    String getEmailFromToken(String token);

    String getRolFromToken(String token);

    /** Valida firma RS256, vigencia y emisor. */
    boolean isTokenValid(String token);

    /** Refresh token opaco e impredecible (HU09 RF07). */
    String generateRefreshToken();

    /** Representación protegida (hash) del refresh token, única que se persiste. */
    String hashRefreshToken(String refreshToken);

    long getAccessExpirationSeconds();

    long getRefreshExpirationDays();
}
