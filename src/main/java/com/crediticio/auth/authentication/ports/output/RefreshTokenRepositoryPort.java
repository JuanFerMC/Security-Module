package com.crediticio.auth.authentication.ports.output;

import com.crediticio.auth.authentication.domain.RefreshToken;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepositoryPort {

    RefreshToken save(RefreshToken refreshToken);

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Marca el token como usado solo si sigue vigente (no usado ni revocado). Operacion atomica en
     * persistencia: ante dos renovaciones concurrentes con el mismo token, solo una retorna true (RF12).
     */
    boolean marcarUsado(Long id, LocalDateTime ahora);

    /** Revoca todos los tokens aun no revocados de la familia (sesion). Retorna cuantos revoco. */
    int revocarFamilia(UUID familiaId, LocalDateTime ahora);
}
