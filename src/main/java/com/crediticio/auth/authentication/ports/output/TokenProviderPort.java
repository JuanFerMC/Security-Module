package com.crediticio.auth.authentication.ports.output;

public interface TokenProviderPort {

    /** Access token JWT: sub = id de usuario, con emisor, audiencia y vigencia corta (HU09 RF03/RF04). */
    String generateToken(Long userId, String email, String rol);

    Long getUserIdFromToken(String token);

    String getEmailFromToken(String token);

    String getRolFromToken(String token);

    /** Valida firma, vigencia, emisor y audiencia (HU09 RF05). */
    boolean isTokenValid(String token);

    /** Refresh token opaco e impredecible (HU09 RF07). */
    String generateRefreshToken();

    /** Representacion protegida (hash) del refresh token, unica que se persiste. */
    String hashRefreshToken(String refreshToken);

    long getAccessExpirationSeconds();

    long getRefreshExpirationDays();
}
