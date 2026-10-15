package com.crediticio.auth.authentication.application;

import com.crediticio.auth.authentication.application.dto.LoginRequest;
import com.crediticio.auth.authentication.application.dto.LoginResponse;
import com.crediticio.auth.authentication.application.dto.TokenValidationResponse;
import com.crediticio.auth.authentication.ports.output.PasswordEncoderPort;
import com.crediticio.auth.authentication.ports.output.TokenProviderPort;
import com.crediticio.auth.roles.domain.Rol;
import com.crediticio.auth.shared.exception.InvalidCredentialsException;
import com.crediticio.auth.users.domain.Usuario;
import com.crediticio.auth.users.ports.output.UserRepositoryPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepositoryPort userRepository;

    @Mock
    private PasswordEncoderPort passwordEncoder;

    @Mock
    private TokenProviderPort tokenProvider;

    @InjectMocks
    private AuthService authService;

    private Usuario buildActiveUser() {
        return Usuario.builder()
                .id(1L).nombre("Juan").email("juan@test.com")
                .passwordHash("hashed").activo(true)
                .rol(Rol.builder().id(1L).nombre("ADMINISTRADOR_RIESGO").build())
                .fechaCreacion(LocalDateTime.now())
                .build();
    }

    @Test
    void login_success() {
        Usuario usuario = buildActiveUser();
        when(userRepository.findByEmail("juan@test.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("password123", "hashed")).thenReturn(true);
        when(tokenProvider.generateToken("juan@test.com", "ADMINISTRADOR_RIESGO")).thenReturn("jwt-token");

        LoginResponse response = authService.login(new LoginRequest("juan@test.com", "password123"));

        assertEquals("jwt-token", response.getToken());
        assertEquals("ADMINISTRADOR_RIESGO", response.getRol());
    }

    @Test
    void login_userNotFound_throws() {
        when(userRepository.findByEmail("noexiste@test.com")).thenReturn(Optional.empty());

        assertThrows(InvalidCredentialsException.class,
                () -> authService.login(new LoginRequest("noexiste@test.com", "password123")));
    }

    @Test
    void login_userInactive_throws() {
        Usuario inactive = Usuario.builder()
                .id(1L).nombre("Juan").email("juan@test.com")
                .passwordHash("hashed").activo(false)
                .rol(Rol.builder().id(1L).nombre("ADMINISTRADOR_RIESGO").build())
                .fechaCreacion(LocalDateTime.now())
                .build();
        when(userRepository.findByEmail("juan@test.com")).thenReturn(Optional.of(inactive));

        assertThrows(InvalidCredentialsException.class,
                () -> authService.login(new LoginRequest("juan@test.com", "password123")));
    }

    @Test
    void login_wrongPassword_throws() {
        Usuario usuario = buildActiveUser();
        when(userRepository.findByEmail("juan@test.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThrows(InvalidCredentialsException.class,
                () -> authService.login(new LoginRequest("juan@test.com", "wrong")));
    }

    @Test
    void refresh_validToken_returnsNewToken() {
        when(tokenProvider.isTokenValid("old-token")).thenReturn(true);
        when(tokenProvider.getEmailFromToken("old-token")).thenReturn("juan@test.com");
        when(tokenProvider.getRolFromToken("old-token")).thenReturn("ADMINISTRADOR_RIESGO");
        when(tokenProvider.generateToken("juan@test.com", "ADMINISTRADOR_RIESGO")).thenReturn("new-token");

        LoginResponse response = authService.refresh("old-token");

        assertEquals("new-token", response.getToken());
        assertEquals("ADMINISTRADOR_RIESGO", response.getRol());
    }

    @Test
    void refresh_invalidToken_throws() {
        when(tokenProvider.isTokenValid("bad-token")).thenReturn(false);

        assertThrows(InvalidCredentialsException.class, () -> authService.refresh("bad-token"));
    }

    @Test
    void validate_validToken_returnsUserInfo() {
        Usuario usuario = buildActiveUser();
        when(tokenProvider.isTokenValid("valid-token")).thenReturn(true);
        when(tokenProvider.getEmailFromToken("valid-token")).thenReturn("juan@test.com");
        when(tokenProvider.getRolFromToken("valid-token")).thenReturn("ADMINISTRADOR_RIESGO");
        when(userRepository.findByEmail("juan@test.com")).thenReturn(Optional.of(usuario));

        TokenValidationResponse response = authService.validate("valid-token");

        assertEquals(1L, response.getUserId());
        assertEquals("juan@test.com", response.getEmail());
        assertEquals("ADMINISTRADOR_RIESGO", response.getRol());
    }

    @Test
    void validate_invalidToken_throws() {
        when(tokenProvider.isTokenValid("bad-token")).thenReturn(false);

        assertThrows(InvalidCredentialsException.class, () -> authService.validate("bad-token"));
    }
}
