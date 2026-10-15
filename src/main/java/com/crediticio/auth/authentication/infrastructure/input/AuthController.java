package com.crediticio.auth.authentication.infrastructure.input;

import com.crediticio.auth.authentication.application.dto.LoginRequest;
import com.crediticio.auth.authentication.application.dto.LoginResponse;
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
@Tag(name = "Autenticacion", description = "Login, renovacion y validacion de tokens JWT. El endpoint login es publico; refresh y validate requieren token valido con rol ADMINISTRADOR_RIESGO o ANALISTA_CREDITO.")
public class AuthController {

    private final AuthInputPort authInputPort;

    @PostMapping("/login")
    @Operation(
            summary = "Iniciar sesion",
            description = "Autentica con email y contrasena. Retorna JWT con rol. Endpoint publico."
    )
    @ApiResponse(responseCode = "200", description = "Autenticacion exitosa, retorna token JWT")
    @ApiResponse(responseCode = "401", description = "Credenciales invalidas o usuario inactivo",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authInputPort.login(request));
    }

    @PostMapping("/refresh")
    @Operation(
            summary = "Renovar token",
            description = "Emite un nuevo JWT a partir de un token valido no expirado. Requiere ADMINISTRADOR_RIESGO o ANALISTA_CREDITO."
    )
    @ApiResponse(responseCode = "200", description = "Token renovado")
    @ApiResponse(responseCode = "401", description = "Token invalido o expirado",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ResponseEntity<LoginResponse> refresh(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        return ResponseEntity.ok(authInputPort.refresh(token));
    }

    @GetMapping("/validate")
    @Operation(
            summary = "Validar token",
            description = "Valida un JWT y retorna la identidad del usuario (id, email, rol). Consumido por el Motor de Scoring."
    )
    @ApiResponse(responseCode = "200", description = "Token valido, retorna identidad del usuario")
    @ApiResponse(responseCode = "401", description = "Token invalido o expirado",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ResponseEntity<TokenValidationResponse> validate(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        return ResponseEntity.ok(authInputPort.validate(token));
    }
}
