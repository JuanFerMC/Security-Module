package com.crediticio.auth.users.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Datos publicos de un usuario (sin password_hash)")
public class UserResponse {

    @Schema(description = "ID del usuario", example = "1")
    private Long id;

    @Schema(description = "Nombre completo", example = "Juan Perez")
    private String nombre;

    @Schema(description = "Correo electronico", example = "juan.perez@ejemplo.com")
    private String email;

    @Schema(description = "Nombre del rol asignado", example = "ANALISTA_CREDITO")
    private String rolNombre;

    @Schema(description = "Estado del usuario", example = "true")
    private boolean activo;

    @Schema(description = "Fecha de creacion")
    private LocalDateTime fechaCreacion;

    @Schema(description = "Fecha de ultima modificacion")
    private LocalDateTime fechaModificacion;
}
