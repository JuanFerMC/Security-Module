package com.crediticio.auth.authentication.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Refresh token de una sesion. Solo se conoce su hash; el valor original nunca se persiste (HU09 RF07).
 */
@Getter
@Builder
@AllArgsConstructor
public class RefreshToken {

    private Long id;
    private Long usuarioId;
    private UUID familiaId;
    private String tokenHash;
    private LocalDateTime emitidoEn;
    private LocalDateTime expiraEn;
    private LocalDateTime usadoEn;
    private LocalDateTime revocadoEn;
}
