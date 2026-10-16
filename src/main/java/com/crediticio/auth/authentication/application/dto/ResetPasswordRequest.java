package com.crediticio.auth.authentication.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Solicitud de restablecimiento de contrasena con token")
public class ResetPasswordRequest {

    @NotBlank
    @Schema(description = "Token UUID recibido por email", example = "550e8400-e29b-41d4-a716-446655440000")
    private String token;

    @NotBlank
    @Size(min = 12, max = 72)
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{12,}$",
            message = "Debe contener al menos una mayuscula, una minuscula, un digito y un caracter especial (@$!%*?&)")
    @Schema(description = "Nueva contrasena (12-72 caracteres, debe incluir mayuscula, minuscula, digito y caracter especial)", example = "NuevaClave2026!")
    private String newPassword;
}
