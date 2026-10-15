package com.crediticio.auth.authentication.application;

import com.crediticio.auth.authentication.application.dto.LoginRequest;
import com.crediticio.auth.authentication.application.dto.LoginResponse;
import com.crediticio.auth.authentication.application.dto.TokenValidationResponse;
import com.crediticio.auth.authentication.domain.RefreshToken;
import com.crediticio.auth.authentication.ports.input.AuthInputPort;
import com.crediticio.auth.authentication.ports.output.PasswordEncoderPort;
import com.crediticio.auth.authentication.ports.output.RefreshTokenRepositoryPort;
import com.crediticio.auth.authentication.ports.output.TokenProviderPort;
import com.crediticio.auth.shared.EmailNormalizer;
import com.crediticio.auth.shared.exception.InvalidCredentialsException;
import com.crediticio.auth.users.domain.Usuario;
import com.crediticio.auth.users.ports.output.UserRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Autenticacion y gestion de sesion (HU09). Ningun log ni respuesta incluye contrasenas, tokens
 * ni hashes (RNF04/RNF05).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService implements AuthInputPort {

    /** Mensaje unico para todo fallo de login: no revela cual credencial fallo (RF02). */
    private static final String CREDENCIALES_INVALIDAS = "Credenciales inválidas";
    private static final String REFRESH_INVALIDO = "Refresh token inválido";
    private static final String TOKEN_INVALIDO = "Token inválido";

    private final UserRepositoryPort userRepository;
    private final PasswordEncoderPort passwordEncoder;
    private final TokenProviderPort tokenProvider;
    private final RefreshTokenRepositoryPort refreshTokenRepository;

    /** Hash de relleno para igualar el tiempo de respuesta cuando el usuario no existe (RF02). */
    private volatile String dummyHash;

    @Override
    @Transactional
    public LoginResponse login(LoginRequest request) {
        String email = EmailNormalizer.normalize(request.getEmail());
        Optional<Usuario> encontrado = userRepository.findByEmail(email);

        // Siempre se evalua un hash BCrypt, exista o no el usuario, para no filtrar existencia por tiempo.
        String hash = encontrado.map(Usuario::getPasswordHash).orElseGet(this::dummyHash);
        boolean passwordOk = passwordEncoder.matches(request.getPassword(), hash);

        if (encontrado.isEmpty() || !passwordOk || !encontrado.get().isActivo()) {
            log.warn("Autenticacion fallida: email={}, motivo={}", email,
                    encontrado.isEmpty() ? "usuario_inexistente"
                            : !passwordOk ? "contrasena_incorrecta" : "usuario_inactivo");
            throw new InvalidCredentialsException(CREDENCIALES_INVALIDAS);
        }

        Usuario usuario = encontrado.get();
        LoginResponse response = emitirSesion(usuario, UUID.randomUUID());
        log.info("Autenticacion exitosa: userId={}, rol={}", usuario.getId(), usuario.getRol().getNombre());
        return response;
    }

    /**
     * Renueva la sesion rotando el refresh token (RF08/RF09). Las comprobaciones que revocan la familia
     * no deben deshacerse al lanzar la excepcion, por eso {@code noRollbackFor}.
     */
    @Override
    @Transactional(noRollbackFor = InvalidCredentialsException.class)
    public LoginResponse refresh(String refreshToken) {
        RefreshToken actual = buscarRefresh(refreshToken)
                .orElseThrow(() -> {
                    log.warn("Renovacion rechazada: refresh token desconocido");
                    return new InvalidCredentialsException(REFRESH_INVALIDO);
                });

        LocalDateTime ahora = LocalDateTime.now();

        // RF10: un token ya rotado o revocado que reaparece indica robo o reutilizacion.
        if (actual.getUsadoEn() != null || actual.getRevocadoEn() != null) {
            refreshTokenRepository.revocarFamilia(actual.getFamiliaId(), ahora);
            log.warn("Reutilizacion de refresh token detectada: userId={}, familia={}; sesion revocada",
                    actual.getUsuarioId(), actual.getFamiliaId());
            throw new InvalidCredentialsException(REFRESH_INVALIDO);
        }

        // RF13: vencido
        if (!actual.getExpiraEn().isAfter(ahora)) {
            log.warn("Renovacion rechazada: refresh token vencido, userId={}", actual.getUsuarioId());
            throw new InvalidCredentialsException(REFRESH_INVALIDO);
        }

        Optional<Usuario> usuario = userRepository.findById(actual.getUsuarioId());
        if (usuario.isEmpty() || !usuario.get().isActivo()) {
            refreshTokenRepository.revocarFamilia(actual.getFamiliaId(), ahora);
            log.warn("Renovacion rechazada: usuario inexistente o inactivo, userId={}", actual.getUsuarioId());
            throw new InvalidCredentialsException(REFRESH_INVALIDO);
        }

        // RF12: canje atomico. Si otra peticion lo canjeo primero, esta pierde y se trata como reutilizacion.
        if (!refreshTokenRepository.marcarUsado(actual.getId(), ahora)) {
            refreshTokenRepository.revocarFamilia(actual.getFamiliaId(), ahora);
            log.warn("Renovacion concurrente con el mismo refresh token: userId={}, familia={}; sesion revocada",
                    actual.getUsuarioId(), actual.getFamiliaId());
            throw new InvalidCredentialsException(REFRESH_INVALIDO);
        }

        LoginResponse response = emitirSesion(usuario.get(), actual.getFamiliaId());
        log.info("Sesion renovada: userId={}, familia={}", actual.getUsuarioId(), actual.getFamiliaId());
        return response;
    }

    @Override
    @Transactional
    public void logout(String refreshToken) {
        // Idempotente: un token desconocido no se distingue de uno ya cerrado (no filtra informacion).
        buscarRefresh(refreshToken).ifPresent(token -> {
            refreshTokenRepository.revocarFamilia(token.getFamiliaId(), LocalDateTime.now());
            log.info("Sesion cerrada: userId={}, familia={}", token.getUsuarioId(), token.getFamiliaId());
        });
    }

    @Override
    public TokenValidationResponse validate(String accessToken) {
        if (!tokenProvider.isTokenValid(accessToken)) {
            throw new InvalidCredentialsException(TOKEN_INVALIDO);
        }

        Long userId = tokenProvider.getUserIdFromToken(accessToken);
        Usuario usuario = userRepository.findById(userId)
                .filter(Usuario::isActivo)
                .orElseThrow(() -> new InvalidCredentialsException(TOKEN_INVALIDO));

        return TokenValidationResponse.builder()
                .userId(usuario.getId())
                .email(usuario.getEmail())
                .rol(usuario.getRol().getNombre())
                .build();
    }

    private Optional<RefreshToken> buscarRefresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return Optional.empty();
        }
        return refreshTokenRepository.findByTokenHash(tokenProvider.hashRefreshToken(refreshToken));
    }

    private LoginResponse emitirSesion(Usuario usuario, UUID familiaId) {
        String accessToken = tokenProvider.generateToken(
                usuario.getId(), usuario.getEmail(), usuario.getRol().getNombre());

        String refreshPlano = tokenProvider.generateRefreshToken();
        LocalDateTime ahora = LocalDateTime.now();
        refreshTokenRepository.save(RefreshToken.builder()
                .usuarioId(usuario.getId())
                .familiaId(familiaId)
                .tokenHash(tokenProvider.hashRefreshToken(refreshPlano))
                .emitidoEn(ahora)
                .expiraEn(ahora.plusDays(tokenProvider.getRefreshExpirationDays()))
                .build());

        return LoginResponse.builder()
                .token(accessToken)
                .refreshToken(refreshPlano)
                .tokenType("Bearer")
                .expiresIn(tokenProvider.getAccessExpirationSeconds())
                .rol(usuario.getRol().getNombre())
                .build();
    }

    private String dummyHash() {
        String valor = dummyHash;
        if (valor == null) {
            valor = passwordEncoder.encode(UUID.randomUUID().toString());
            dummyHash = valor;
        }
        return valor;
    }
}
