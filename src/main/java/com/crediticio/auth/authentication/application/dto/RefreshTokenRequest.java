package com.crediticio.auth.authentication.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Refresh token para renovar la sesion o cerrarla")
public class RefreshTokenRequest {

    // Sin validaciones de bean a proposito: un token ausente, vacio o demasiado largo es un token
    // invalido y debe responder 401 (HU09 RF13), no 400.
    @Schema(description = "Refresh token recibido en el login o en la ultima renovacion", example = "q3k2...")
    private String refreshToken;
}
