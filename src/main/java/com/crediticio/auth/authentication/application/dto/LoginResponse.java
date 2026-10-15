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
@Schema(description = "Respuesta de autenticacion o renovacion exitosa")
public class LoginResponse {

    @Schema(description = "Access token JWT firmado (vigencia de 15 minutos)", example = "eyJhbGciOiJIUzI1NiIs...")
    private String token;

    @Schema(description = "Refresh token opaco de un solo uso (vigencia maxima de 7 dias). Se rota en cada renovacion.",
            example = "q3k2...")
    private String refreshToken;

    @Schema(description = "Tipo de token para el encabezado Authorization", example = "Bearer")
    private String tokenType;

    @Schema(description = "Segundos de vigencia del access token", example = "900")
    private long expiresIn;

    @Schema(description = "Rol del usuario autenticado", example = "ADMINISTRADOR_RIESGO")
    private String rol;
}
