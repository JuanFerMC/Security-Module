package com.crediticio.auth.authentication.application;

import com.crediticio.auth.authentication.application.dto.LoginRequest;
import com.crediticio.auth.authentication.application.dto.LoginResponse;
import com.crediticio.auth.authentication.application.dto.TokenValidationResponse;
import com.crediticio.auth.authentication.domain.RefreshToken;
import com.crediticio.auth.authentication.ports.output.PasswordEncoderPort;
import com.crediticio.auth.authentication.ports.output.RefreshTokenRepositoryPort;
import com.crediticio.auth.authentication.ports.output.TokenProviderPort;
import com.crediticio.auth.roles.domain.Rol;
import com.crediticio.auth.shared.exception.InvalidCredentialsException;
import com.crediticio.auth.users.domain.Usuario;
import com.crediticio.auth.users.ports.output.UserRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepositoryPort userRepository;

    @Mock
    private PasswordEncoderPort passwordEncoder;

    @Mock
    private TokenProviderPort tokenProvider;

    @Mock
    private RefreshTokenRepositoryPort refreshTokenRepository;

    @InjectMocks
    private AuthService authService;

    private final UUID familia = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        lenient().when(tokenProvider.hashRefreshToken(anyString())).thenAnswer(inv -> "h:" + inv.getArgument(0));
        lenient().when(tokenProvider.getRefreshExpirationDays()).thenReturn(7L);
        lenient().when(tokenProvider.getAccessExpirationSeconds()).thenReturn(900L);
    }

    private Usuario user(boolean activo) {
        return Usuario.builder()
                .id(1L).nombre("Juan").email("juan@test.com")
                .passwordHash("hashed").activo(activo)
                .rol(Rol.builder().id(1L).nombre("ADMINISTRADOR_RIESGO").build())
                .fechaCreacion(LocalDateTime.now())
                .build();
    }

    private RefreshToken refresh(LocalDateTime expira, LocalDateTime usado, LocalDateTime revocado) {
        return RefreshToken.builder()
                .id(10L).usuarioId(1L).familiaId(familia).tokenHash("h:viejo")
                .emitidoEn(LocalDateTime.now().minusDays(1)).expiraEn(expira)
                .usadoEn(usado).revocadoEn(revocado).build();
    }

    // ---------- login ----------

    @Test
    void login_success_issuesAccessAndRefresh() {
        when(userRepository.findByEmail("juan@test.com")).thenReturn(Optional.of(user(true)));
        when(passwordEncoder.matches("password123", "hashed")).thenReturn(true);
        when(tokenProvider.generateToken(1L, "juan@test.com", "ADMINISTRADOR_RIESGO")).thenReturn("jwt");
        when(tokenProvider.generateRefreshToken()).thenReturn("refresh-plano");

        LoginResponse response = authService.login(new LoginRequest("Juan@Test.com", "password123"));

        assertEquals("jwt", response.getToken());
        assertEquals("refresh-plano", response.getRefreshToken());
        assertEquals("Bearer", response.getTokenType());
        assertEquals(900L, response.getExpiresIn());
        assertEquals("ADMINISTRADOR_RIESGO", response.getRol());

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        assertEquals("h:refresh-plano", captor.getValue().getTokenHash());
        assertNotEquals("refresh-plano", captor.getValue().getTokenHash());
        assertNotNull(captor.getValue().getFamiliaId());
    }

    @Test
    void login_userNotFound_throwsAndStillComparesHash() {
        when(userRepository.findByEmail("noexiste@test.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("dummy");
        when(passwordEncoder.matches("password123", "dummy")).thenReturn(false);

        InvalidCredentialsException ex = assertThrows(InvalidCredentialsException.class,
                () -> authService.login(new LoginRequest("noexiste@test.com", "password123")));

        assertEquals("Credenciales inválidas", ex.getMessage());
        verify(passwordEncoder).matches("password123", "dummy");
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void login_userInactive_sameMessageAsWrongPassword() {
        when(userRepository.findByEmail("juan@test.com")).thenReturn(Optional.of(user(false)));
        when(passwordEncoder.matches("password123", "hashed")).thenReturn(true);

        InvalidCredentialsException ex = assertThrows(InvalidCredentialsException.class,
                () -> authService.login(new LoginRequest("juan@test.com", "password123")));

        assertEquals("Credenciales inválidas", ex.getMessage());
    }

    @Test
    void login_wrongPassword_throws() {
        when(userRepository.findByEmail("juan@test.com")).thenReturn(Optional.of(user(true)));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        InvalidCredentialsException ex = assertThrows(InvalidCredentialsException.class,
                () -> authService.login(new LoginRequest("juan@test.com", "wrong")));

        assertEquals("Credenciales inválidas", ex.getMessage());
    }

    // ---------- refresh ----------

    @Test
    void refresh_valid_rotatesToken() {
        RefreshToken actual = refresh(LocalDateTime.now().plusDays(3), null, null);
        when(refreshTokenRepository.findByTokenHash("h:viejo")).thenReturn(Optional.of(actual));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(true)));
        when(refreshTokenRepository.marcarUsado(eq(10L), any())).thenReturn(true);
        when(tokenProvider.generateToken(1L, "juan@test.com", "ADMINISTRADOR_RIESGO")).thenReturn("nuevo-jwt");
        when(tokenProvider.generateRefreshToken()).thenReturn("nuevo-refresh");

        LoginResponse response = authService.refresh("viejo");

        assertEquals("nuevo-jwt", response.getToken());
        assertEquals("nuevo-refresh", response.getRefreshToken());
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        assertEquals(familia, captor.getValue().getFamiliaId());
        verify(refreshTokenRepository, never()).revocarFamilia(any(), any());
    }

    @Test
    void refresh_unknownToken_throws() {
        when(refreshTokenRepository.findByTokenHash("h:desconocido")).thenReturn(Optional.empty());

        assertThrows(InvalidCredentialsException.class, () -> authService.refresh("desconocido"));
        verify(refreshTokenRepository, never()).revocarFamilia(any(), any());
    }

    @Test
    void refresh_oversizedToken_throwsWithoutLookup() {
        String largo = "a".repeat(600);

        assertThrows(InvalidCredentialsException.class, () -> authService.refresh(largo));
        verify(refreshTokenRepository, never()).findByTokenHash(anyString());
    }

    @Test
    void logout_oversizedToken_isIdempotentWithoutLookup() {
        assertDoesNotThrow(() -> authService.logout("a".repeat(600)));
        verify(refreshTokenRepository, never()).findByTokenHash(anyString());
    }

    @Test
    void refresh_blankToken_throws() {
        assertThrows(InvalidCredentialsException.class, () -> authService.refresh("  "));
        assertThrows(InvalidCredentialsException.class, () -> authService.refresh(null));
    }

    @Test
    void refresh_reusedToken_revokesWholeFamily() {
        RefreshToken usado = refresh(LocalDateTime.now().plusDays(3), LocalDateTime.now().minusMinutes(5), null);
        when(refreshTokenRepository.findByTokenHash("h:viejo")).thenReturn(Optional.of(usado));

        assertThrows(InvalidCredentialsException.class, () -> authService.refresh("viejo"));

        verify(refreshTokenRepository).revocarFamilia(eq(familia), any());
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void refresh_revokedToken_revokesFamilyAndThrows() {
        RefreshToken revocado = refresh(LocalDateTime.now().plusDays(3), null, LocalDateTime.now().minusMinutes(5));
        when(refreshTokenRepository.findByTokenHash("h:viejo")).thenReturn(Optional.of(revocado));

        assertThrows(InvalidCredentialsException.class, () -> authService.refresh("viejo"));

        verify(refreshTokenRepository).revocarFamilia(eq(familia), any());
    }

    @Test
    void refresh_expiredToken_throwsWithoutIssuing() {
        RefreshToken vencido = refresh(LocalDateTime.now().minusMinutes(1), null, null);
        when(refreshTokenRepository.findByTokenHash("h:viejo")).thenReturn(Optional.of(vencido));

        assertThrows(InvalidCredentialsException.class, () -> authService.refresh("viejo"));

        verify(refreshTokenRepository, never()).save(any());
        verify(refreshTokenRepository, never()).marcarUsado(anyLong(), any());
    }

    @Test
    void refresh_inactiveUser_revokesFamilyAndThrows() {
        RefreshToken actual = refresh(LocalDateTime.now().plusDays(3), null, null);
        when(refreshTokenRepository.findByTokenHash("h:viejo")).thenReturn(Optional.of(actual));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(false)));

        assertThrows(InvalidCredentialsException.class, () -> authService.refresh("viejo"));

        verify(refreshTokenRepository).revocarFamilia(eq(familia), any());
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void refresh_lostRace_revokesFamilyAndDoesNotIssue() {
        RefreshToken actual = refresh(LocalDateTime.now().plusDays(3), null, null);
        when(refreshTokenRepository.findByTokenHash("h:viejo")).thenReturn(Optional.of(actual));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(true)));
        when(refreshTokenRepository.marcarUsado(eq(10L), any())).thenReturn(false);

        assertThrows(InvalidCredentialsException.class, () -> authService.refresh("viejo"));

        verify(refreshTokenRepository).revocarFamilia(eq(familia), any());
        verify(refreshTokenRepository, never()).save(any());
    }

    // ---------- logout ----------

    @Test
    void logout_revokesFamily() {
        RefreshToken actual = refresh(LocalDateTime.now().plusDays(3), null, null);
        when(refreshTokenRepository.findByTokenHash("h:viejo")).thenReturn(Optional.of(actual));

        authService.logout("viejo");

        verify(refreshTokenRepository).revocarFamilia(eq(familia), any());
    }

    @Test
    void logout_unknownToken_isIdempotent() {
        when(refreshTokenRepository.findByTokenHash("h:x")).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> authService.logout("x"));
        verify(refreshTokenRepository, never()).revocarFamilia(any(), any());
    }

    // ---------- validate ----------

    @Test
    void validate_validToken_returnsUserInfoFromDatabase() {
        when(tokenProvider.isTokenValid("valid-token")).thenReturn(true);
        when(tokenProvider.getUserIdFromToken("valid-token")).thenReturn(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(true)));

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

    @Test
    void validate_inactiveUser_throws() {
        when(tokenProvider.isTokenValid("t")).thenReturn(true);
        when(tokenProvider.getUserIdFromToken("t")).thenReturn(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(false)));

        assertThrows(InvalidCredentialsException.class, () -> authService.validate("t"));
    }
}
