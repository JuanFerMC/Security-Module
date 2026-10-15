package com.crediticio.auth.users.ports.output;

import com.crediticio.auth.users.domain.Usuario;

import java.util.List;
import java.util.Optional;

public interface UserRepositoryPort {

    Usuario save(Usuario usuario);

    Optional<Usuario> findById(Long id);

    Optional<Usuario> findByEmail(String email);

    List<Usuario> findAll();

    boolean existsByEmail(String email);

    boolean existsByRolNombre(String nombreRol);
}
