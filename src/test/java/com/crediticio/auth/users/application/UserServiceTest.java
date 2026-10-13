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
import com.crediticio.auth.users.ports.output.UserRepositoryPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.security.access.AccessDeniedException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepositoryPort userRepository;

    @Mock
    private RolRepositoryPort rolRepository;

    @Mock
    private PasswordEncoderPort passwordEncoder;

    @InjectMocks
    private UserService userService;

    private final Rol adminRol = Rol.builder().id(1L).nombre("ADMIN").build();

    private Usuario buildUsuario(Long id, String nombre, String email) {
        return Usuario.builder()
                .id(id)
                .nombre(nombre)
                .email(email)
                .passwordHash("hashed")
                .rol(adminRol)
                .activo(true)
                .fechaCreacion(LocalDateTime.now())
                .build();
    }

    @Test
    void createUser_success() {
        CreateUserRequest request = new CreateUserRequest("Juan", "juan@test.com", "password123", 1L);

        when(userRepository.existsByEmail("juan@test.com")).thenReturn(false);
        when(rolRepository.findById(1L)).thenReturn(Optional.of(adminRol));
        when(passwordEncoder.encode("password123")).thenReturn("hashed");
        when(userRepository.save(any(Usuario.class))).thenAnswer(inv -> {
            Usuario u = inv.getArgument(0);
            return Usuario.builder()
                    .id(1L).nombre(u.getNombre()).email(u.getEmail())
                    .passwordHash(u.getPasswordHash()).rol(u.getRol())
                    .activo(u.isActivo()).fechaCreacion(LocalDateTime.now())
                    .build();
        });

        UserResponse response = userService.createUser(request);

        assertEquals("Juan", response.getNombre());
        assertEquals("juan@test.com", response.getEmail());
        assertEquals("ADMIN", response.getRolNombre());
        assertTrue(response.isActivo());
        verify(passwordEncoder).encode("password123");
    }

    @Test
    void createUser_duplicateEmail_throws() {
        CreateUserRequest request = new CreateUserRequest("Juan", "juan@test.com", "password123", 1L);
        when(userRepository.existsByEmail("juan@test.com")).thenReturn(true);

        assertThrows(DuplicateEmailException.class, () -> userService.createUser(request));
        verify(userRepository, never()).save(any());
    }

    @Test
    void createUser_rolNotFound_throws() {
        CreateUserRequest request = new CreateUserRequest("Juan", "juan@test.com", "password123", 99L);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(rolRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RolNotFoundException.class, () -> userService.createUser(request));
    }

    @Test
    void getAllUsers_returnsList() {
        List<Usuario> usuarios = List.of(
                buildUsuario(1L, "Juan", "juan@test.com"),
                buildUsuario(2L, "Maria", "maria@test.com")
        );
        when(userRepository.findAll()).thenReturn(usuarios);

        List<UserResponse> result = userService.getAllUsers();

        assertEquals(2, result.size());
        assertEquals("Juan", result.get(0).getNombre());
        assertEquals("Maria", result.get(1).getNombre());
    }

    @Test
    void getUserById_found() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(buildUsuario(1L, "Juan", "juan@test.com")));

        UserResponse response = userService.getUserById(1L);

        assertEquals("Juan", response.getNombre());
    }

    @Test
    void getUserById_notFound_throws() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> userService.getUserById(99L));
    }

    @Test
    void toggleStatus_deactivate() {
        Usuario usuario = buildUsuario(1L, "Juan", "juan@test.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(userRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        UserResponse response = userService.toggleStatus(1L, new ToggleStatusRequest(false));

        assertFalse(response.isActivo());
    }

    @Test
    void toggleStatus_notFound_throws() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class,
                () -> userService.toggleStatus(99L, new ToggleStatusRequest(false)));
    }

    // --- changePassword tests ---

    @Test
    void changePassword_asAdmin_resetsWithoutCurrentPassword() {
        Usuario usuario = buildUsuario(1L, "Juan", "juan@test.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.encode("newPass123")).thenReturn("newHashed");
        when(userRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        userService.changePassword(1L, new ChangePasswordRequest(null, "newPass123"), "admin@test.com", true);

        verify(passwordEncoder).encode("newPass123");
        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verify(userRepository).save(any(Usuario.class));
    }

    @Test
    void changePassword_asAnalista_ownPassword_success() {
        Usuario usuario = buildUsuario(1L, "Juan", "juan@test.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("currentPass", "hashed")).thenReturn(true);
        when(passwordEncoder.encode("newPass123")).thenReturn("newHashed");
        when(userRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        userService.changePassword(1L, new ChangePasswordRequest("currentPass", "newPass123"), "juan@test.com", false);

        verify(passwordEncoder).matches("currentPass", "hashed");
        verify(passwordEncoder).encode("newPass123");
    }

    @Test
    void changePassword_asAnalista_otherUser_throws() {
        Usuario usuario = buildUsuario(1L, "Juan", "juan@test.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(usuario));

        assertThrows(AccessDeniedException.class,
                () -> userService.changePassword(1L, new ChangePasswordRequest("pass", "newPass123"), "otro@test.com", false));
    }

    @Test
    void changePassword_asAnalista_wrongCurrentPassword_throws() {
        Usuario usuario = buildUsuario(1L, "Juan", "juan@test.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("wrongCurrent", "hashed")).thenReturn(false);

        assertThrows(InvalidCredentialsException.class,
                () -> userService.changePassword(1L, new ChangePasswordRequest("wrongCurrent", "newPass123"), "juan@test.com", false));
    }

    @Test
    void changePassword_asAnalista_noCurrentPassword_throws() {
        Usuario usuario = buildUsuario(1L, "Juan", "juan@test.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(usuario));

        assertThrows(InvalidCredentialsException.class,
                () -> userService.changePassword(1L, new ChangePasswordRequest(null, "newPass123"), "juan@test.com", false));
    }

    @Test
    void changePassword_userNotFound_throws() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class,
                () -> userService.changePassword(99L, new ChangePasswordRequest("p", "newPass123"), "x@test.com", true));
    }
}
