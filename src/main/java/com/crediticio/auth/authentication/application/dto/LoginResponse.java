package com.crediticio.auth.authentication.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Respuesta de autenticacion exitosa")
public class LoginResponse {

    @Schema(description = "Token JWT firmado", example = "eyJhbGciOiJIUzI1NiIs...")
    private String token;

    @Schema(description = "Rol del usuario autenticado", example = "ADMINISTRADOR_RIESGO")
    private String rol;
}
