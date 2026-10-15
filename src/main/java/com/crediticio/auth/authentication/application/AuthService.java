package com.crediticio.auth.authentication.application;

import com.crediticio.auth.authentication.application.dto.LoginRequest;
import com.crediticio.auth.authentication.application.dto.LoginResponse;
import com.crediticio.auth.authentication.application.dto.TokenValidationResponse;
import com.crediticio.auth.authentication.ports.input.AuthInputPort;
import com.crediticio.auth.authentication.ports.output.PasswordEncoderPort;
import com.crediticio.auth.authentication.ports.output.TokenProviderPort;
import com.crediticio.auth.shared.EmailNormalizer;
import com.crediticio.auth.shared.exception.InvalidCredentialsException;
import com.crediticio.auth.users.domain.Usuario;
import com.crediticio.auth.users.ports.output.UserRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService implements AuthInputPort {

    private final UserRepositoryPort userRepository;
    private final PasswordEncoderPort passwordEncoder;
    private final TokenProviderPort tokenProvider;

    @Override
    public LoginResponse login(LoginRequest request) {
        Usuario usuario = userRepository.findByEmail(EmailNormalizer.normalize(request.getEmail()))
                .orElseThrow(() -> new InvalidCredentialsException("Credenciales inválidas"));

        if (!usuario.isActivo()) {
            throw new InvalidCredentialsException("Usuario desactivado");
        }

        if (!passwordEncoder.matches(request.getPassword(), usuario.getPasswordHash())) {
            throw new InvalidCredentialsException("Credenciales inválidas");
        }

        String token = tokenProvider.generateToken(usuario.getEmail(), usuario.getRol().getNombre());

        return LoginResponse.builder()
                .token(token)
                .rol(usuario.getRol().getNombre())
                .build();
    }

    @Override
    public LoginResponse refresh(String token) {
        if (!tokenProvider.isTokenValid(token)) {
            throw new InvalidCredentialsException("Token inválido");
        }

        String email = tokenProvider.getEmailFromToken(token);
        String rol = tokenProvider.getRolFromToken(token);
        String newToken = tokenProvider.generateToken(email, rol);

        return LoginResponse.builder()
                .token(newToken)
                .rol(rol)
                .build();
    }

    @Override
    public TokenValidationResponse validate(String token) {
        if (!tokenProvider.isTokenValid(token)) {
            throw new InvalidCredentialsException("Token inválido");
        }

        String email = tokenProvider.getEmailFromToken(token);
        String rol = tokenProvider.getRolFromToken(token);

        Usuario usuario = userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("Usuario no encontrado"));

        return TokenValidationResponse.builder()
                .userId(usuario.getId())
                .email(usuario.getEmail())
                .rol(rol)
                .build();
    }
}
