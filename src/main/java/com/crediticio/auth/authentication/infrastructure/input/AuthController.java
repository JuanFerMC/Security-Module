package com.crediticio.auth.authentication.infrastructure.input;

import com.crediticio.auth.authentication.application.dto.LoginRequest;
import com.crediticio.auth.authentication.application.dto.LoginResponse;
import com.crediticio.auth.authentication.application.dto.RefreshTokenRequest;
import com.crediticio.auth.authentication.application.dto.TokenValidationResponse;
import com.crediticio.auth.authentication.ports.input.AuthInputPort;
import com.crediticio.auth.shared.response.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Autenticacion", description = "Login, renovacion con refresh token, cierre de sesion y validacion de tokens JWT. login, refresh y logout son publicos (se protegen con credenciales o refresh token); validate requiere un access token valido.")
public class AuthController {

    private static final String BEARER = "Bearer ";

    private final AuthInputPort authInputPort;

    @PostMapping("/login")
    @Operation(
            summary = "Iniciar sesion",
            description = "Autentica con email y contrasena. Retorna un access token JWT de 15 minutos y un refresh token de un solo uso (max. 7 dias). Endpoint publico."
    )
    @ApiResponse(responseCode = "200", description = "Autenticacion exitosa")
    @ApiResponse(responseCode = "400", description = "Datos de entrada invalidos",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "401", description = "Credenciales invalidas (no se indica cual fallo)",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authInputPort.login(request));
    }

    @PostMapping("/refresh")
    @Operation(
            summary = "Renovar sesion",
            description = "Canjea un refresh token valido por un nuevo access token y un nuevo refresh token. El refresh token usado queda invalidado. Si se reutiliza un token ya rotado se revoca toda la sesion."
    )
    @ApiResponse(responseCode = "200", description = "Sesion renovada")
    @ApiResponse(responseCode = "400", description = "Cuerpo que no es JSON valido",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "401", description = "Refresh token ausente, vacio, vencido, revocado, ya usado o invalido",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ResponseEntity<LoginResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authInputPort.refresh(request.getRefreshToken()));
    }

    @PostMapping("/logout")
    @Operation(
            summary = "Cerrar sesion",
            description = "Invalida la sesion asociada al refresh token. Es idempotente: siempre responde 204."
    )
    @ApiResponse(responseCode = "204", description = "Sesion cerrada")
    @ApiResponse(responseCode = "400", description = "Cuerpo que no es JSON valido",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authInputPort.logout(request.getRefreshToken());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/validate")
    @Operation(
            summary = "Validar token",
            description = "Valida un access token JWT y retorna la identidad del usuario (id, email, rol). Consumido por el Motor de Scoring."
    )
    @ApiResponse(responseCode = "200", description = "Token valido, retorna identidad del usuario")
    @ApiResponse(responseCode = "401", description = "Token invalido o expirado",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ResponseEntity<TokenValidationResponse> validate(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.startsWith(BEARER) ? authHeader.substring(BEARER.length()) : authHeader;
        return ResponseEntity.ok(authInputPort.validate(token));
    }
}
