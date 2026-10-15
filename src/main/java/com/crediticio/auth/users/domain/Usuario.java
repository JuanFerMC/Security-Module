package com.crediticio.auth.users.domain;

import com.crediticio.auth.roles.domain.Rol;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class Usuario {

    private Long id;
    private String nombre;
    private String email;
    private String passwordHash;
    private Rol rol;
    private boolean activo;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaModificacion;
    private String creadoPor;
}
