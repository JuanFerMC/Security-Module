package com.crediticio.auth.users.application.dto;

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
@Schema(description = "Solicitud de cambio de contrasena")
public class ChangePasswordRequest {

    @Schema(description = "Contrasena actual (obligatorio para usuarios no ADMIN que cambian su propia contrasena)", example = "MiClaveActual1!")
    private String currentPassword;

    @NotBlank
    @Size(min = 8)
    @Schema(description = "Nueva contrasena (minimo 8 caracteres)", example = "NuevaClave123!")
    private String newPassword;
}
