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
@Schema(description = "Identidad extraida de un token JWT valido")
public class TokenValidationResponse {

    @Schema(description = "ID del usuario", example = "1")
    private Long userId;

    @Schema(description = "Email del usuario", example = "admin@crediticio.com")
    private String email;

    @Schema(description = "Rol del usuario", example = "ADMINISTRADOR_RIESGO")
    private String rol;
}
