package com.crediticio.auth.authentication.application;

import com.crediticio.auth.authentication.application.dto.ForgotPasswordRequest;
import com.crediticio.auth.authentication.application.dto.ResetPasswordRequest;
import com.crediticio.auth.authentication.domain.PasswordResetToken;
import com.crediticio.auth.authentication.ports.input.PasswordResetInputPort;
import com.crediticio.auth.authentication.ports.output.NotificationPort;
import com.crediticio.auth.authentication.ports.output.PasswordEncoderPort;
import com.crediticio.auth.authentication.ports.output.PasswordResetTokenRepositoryPort;
import com.crediticio.auth.shared.EmailNormalizer;
import com.crediticio.auth.shared.exception.InvalidCredentialsException;
import com.crediticio.auth.users.domain.Usuario;
import com.crediticio.auth.users.ports.output.UserRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService implements PasswordResetInputPort {

    private static final int TOKEN_EXPIRATION_MINUTES = 30;

    private final UserRepositoryPort userRepository;
    private final PasswordResetTokenRepositoryPort tokenRepository;
    private final PasswordEncoderPort passwordEncoder;
    private final NotificationPort notificationPort;

    @Override
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        String email = EmailNormalizer.normalize(request.getEmail());

        userRepository.findByEmail(email).ifPresent(usuario -> {
            if (!usuario.isActivo()) {
                return;
            }

            tokenRepository.deleteByEmail(email);

            String rawToken = UUID.randomUUID().toString();

            PasswordResetToken resetToken = PasswordResetToken.builder()
                    .token(rawToken)
                    .email(email)
                    .expiresAt(LocalDateTime.now().plusMinutes(TOKEN_EXPIRATION_MINUTES))
                    .used(false)
                    .build();

            tokenRepository.save(resetToken);
            notificationPort.sendPasswordResetEmail(email, rawToken);
            log.info("Token de recuperacion generado para: {}", email);
        });
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        PasswordResetToken resetToken = tokenRepository.findByToken(request.getToken())
                .orElseThrow(() -> new InvalidCredentialsException("Token de recuperación inválido"));

        if (!resetToken.isValid()) {
            throw new InvalidCredentialsException("Token de recuperación expirado o ya utilizado");
        }

        Usuario usuario = userRepository.findByEmail(resetToken.getEmail())
                .orElseThrow(() -> new InvalidCredentialsException("Usuario no encontrado"));

        String newHash = passwordEncoder.encode(request.getNewPassword());

        Usuario updated = Usuario.builder()
                .id(usuario.getId())
                .nombre(usuario.getNombre())
                .email(usuario.getEmail())
                .passwordHash(newHash)
                .rol(usuario.getRol())
                .activo(usuario.isActivo())
                .fechaCreacion(usuario.getFechaCreacion())
                .fechaModificacion(LocalDateTime.now())
                .build();

        userRepository.save(updated);

        PasswordResetToken usedToken = PasswordResetToken.builder()
                .id(resetToken.getId())
                .token(resetToken.getToken())
                .email(resetToken.getEmail())
                .expiresAt(resetToken.getExpiresAt())
                .used(true)
                .build();

        tokenRepository.save(usedToken);
        log.info("Contrasena restablecida para: {}", resetToken.getEmail());
    }
}
