package com.crediticio.auth.users.infrastructure.output;

import com.crediticio.auth.roles.domain.Rol;
import com.crediticio.auth.roles.infrastructure.output.RolJpaRepository;
import com.crediticio.auth.users.domain.Usuario;
import com.crediticio.auth.users.ports.output.UserRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class UsuarioJpaAdapter implements UserRepositoryPort {

    private final UsuarioJpaRepository usuarioJpaRepository;
    private final RolJpaRepository rolJpaRepository;

    @Override
    public Usuario save(Usuario usuario) {
        UsuarioJpaEntity entity = toJpaEntity(usuario);
        UsuarioJpaEntity saved = usuarioJpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<Usuario> findById(Long id) {
        return usuarioJpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<Usuario> findByUuidPublico(UUID uuidPublico) {
        return usuarioJpaRepository.findByUuidPublico(uuidPublico).map(this::toDomain);
    }

    @Override
    public Optional<Usuario> findByEmail(String email) {
        return usuarioJpaRepository.findByEmail(email).map(this::toDomain);
    }

    @Override
    public List<Usuario> findAll() {
        return usuarioJpaRepository.findAll().stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public boolean existsByEmail(String email) {
        return usuarioJpaRepository.existsByEmail(email);
    }

    @Override
    public boolean existsByRolNombre(String nombreRol) {
        return usuarioJpaRepository.existsByRol_Nombre(nombreRol);
    }

    private Usuario toDomain(UsuarioJpaEntity entity) {
        return Usuario.builder()
                .id(entity.getId())
                .uuidPublico(entity.getUuidPublico())
                .nombre(entity.getNombre())
                .email(entity.getEmail())
                .passwordHash(entity.getPasswordHash())
                .rol(Rol.builder()
                        .id(entity.getRol().getId())
                        .nombre(entity.getRol().getNombre())
                        .build())
                .activo(entity.getActivo())
                .fechaCreacion(entity.getFechaCreacion())
                .fechaModificacion(entity.getFechaModificacion())
                .creadoPor(entity.getCreadoPor())
                .build();
    }

    private UsuarioJpaEntity toJpaEntity(Usuario usuario) {
        UsuarioJpaEntity entity = new UsuarioJpaEntity();
        entity.setId(usuario.getId());
        entity.setNombre(usuario.getNombre());
        entity.setEmail(usuario.getEmail());
        entity.setPasswordHash(usuario.getPasswordHash());
        entity.setRol(rolJpaRepository.getReferenceById(usuario.getRol().getId()));
        entity.setActivo(usuario.isActivo());
        entity.setFechaCreacion(usuario.getFechaCreacion());
        entity.setFechaModificacion(usuario.getFechaModificacion());
        entity.setCreadoPor(usuario.getCreadoPor());
        return entity;
    }
}
