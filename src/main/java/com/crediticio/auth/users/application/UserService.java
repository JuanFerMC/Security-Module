package com.crediticio.auth.users.application;

import com.crediticio.auth.authentication.ports.output.PasswordEncoderPort;
import com.crediticio.auth.roles.domain.Rol;
import com.crediticio.auth.roles.ports.output.RolRepositoryPort;
import com.crediticio.auth.shared.exception.DuplicateEmailException;
import com.crediticio.auth.shared.exception.InvalidCredentialsException;
import com.crediticio.auth.shared.exception.RolNotFoundException;
import com.crediticio.auth.shared.exception.UserNotFoundException;
import com.crediticio.auth.users.application.dto.ChangePasswordRequest;
import com.crediticio.auth.users.application.dto.CreateUserRequest;
import com.crediticio.auth.users.application.dto.ToggleStatusRequest;
import com.crediticio.auth.users.application.dto.UserResponse;
import com.crediticio.auth.users.domain.Usuario;
import com.crediticio.auth.users.ports.input.ChangePasswordInputPort;
import com.crediticio.auth.users.ports.input.CreateUserInputPort;
import com.crediticio.auth.users.ports.input.GetUserInputPort;
import com.crediticio.auth.users.ports.input.ToggleUserStatusInputPort;
import com.crediticio.auth.users.ports.output.UserRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService implements CreateUserInputPort, GetUserInputPort, ToggleUserStatusInputPort, ChangePasswordInputPort {

    private final UserRepositoryPort userRepository;
    private final RolRepositoryPort rolRepository;
    private final PasswordEncoderPort passwordEncoder;

    @Override
    public UserResponse createUser(CreateUserRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateEmailException("El email ya está registrado: " + request.getEmail());
        }

        Rol rol = rolRepository.findById(request.getIdRol())
                .orElseThrow(() -> new RolNotFoundException("Rol no encontrado con id: " + request.getIdRol()));

        Usuario usuario = Usuario.builder()
                .nombre(request.getNombre())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .rol(rol)
                .activo(true)
                .build();

        Usuario saved = userRepository.save(usuario);
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserById(Long id) {
        return userRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new UserNotFoundException("Usuario no encontrado con id: " + id));
    }

    @Override
    public UserResponse toggleStatus(Long id, ToggleStatusRequest request) {
        Usuario usuario = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("Usuario no encontrado con id: " + id));

        Usuario updated = Usuario.builder()
                .id(usuario.getId())
                .nombre(usuario.getNombre())
                .email(usuario.getEmail())
                .passwordHash(usuario.getPasswordHash())
                .rol(usuario.getRol())
                .activo(request.getActivo())
                .fechaCreacion(usuario.getFechaCreacion())
                .build();

        Usuario saved = userRepository.save(updated);
        return toResponse(saved);
    }

    @Override
    public void changePassword(Long userId, ChangePasswordRequest request, String authenticatedEmail, boolean isAdmin) {
        Usuario usuario = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("Usuario no encontrado con id: " + userId));

        if (!isAdmin && !usuario.getEmail().equals(authenticatedEmail)) {
            throw new AccessDeniedException("Solo puede cambiar su propia contrasena");
        }

        if (!isAdmin) {
            if (request.getCurrentPassword() == null || request.getCurrentPassword().isBlank()) {
                throw new InvalidCredentialsException("Debe proporcionar la contrasena actual");
            }
            if (!passwordEncoder.matches(request.getCurrentPassword(), usuario.getPasswordHash())) {
                throw new InvalidCredentialsException("La contrasena actual es incorrecta");
            }
        }

        Usuario updated = Usuario.builder()
                .id(usuario.getId())
                .nombre(usuario.getNombre())
                .email(usuario.getEmail())
                .passwordHash(passwordEncoder.encode(request.getNewPassword()))
                .rol(usuario.getRol())
                .activo(usuario.isActivo())
                .fechaCreacion(usuario.getFechaCreacion())
                .build();

        userRepository.save(updated);
    }

    private UserResponse toResponse(Usuario usuario) {
        return UserResponse.builder()
                .id(usuario.getId())
                .nombre(usuario.getNombre())
                .email(usuario.getEmail())
                .rolNombre(usuario.getRol().getNombre())
                .activo(usuario.isActivo())
                .fechaCreacion(usuario.getFechaCreacion())
                .fechaModificacion(usuario.getFechaModificacion())
                .build();
    }
}
