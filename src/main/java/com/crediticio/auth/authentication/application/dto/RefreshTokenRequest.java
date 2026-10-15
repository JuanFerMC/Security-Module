package com.crediticio.auth.authentication.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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

    @NotBlank
    @Size(max = 512)
    @Schema(description = "Refresh token recibido en el login o en la ultima renovacion", example = "q3k2...")
    private String refreshToken;
}
