package com.crediticio.auth.users.infrastructure.input;

import com.crediticio.auth.shared.response.ApiError;
import com.crediticio.auth.users.application.dto.ChangePasswordRequest;
import com.crediticio.auth.users.application.dto.CreateUserRequest;
import com.crediticio.auth.users.application.dto.ToggleStatusRequest;
import com.crediticio.auth.users.application.dto.UserResponse;
import com.crediticio.auth.users.ports.input.ChangePasswordInputPort;
import com.crediticio.auth.users.ports.input.CreateUserInputPort;
import com.crediticio.auth.users.ports.input.GetUserInputPort;
import com.crediticio.auth.users.ports.input.ToggleUserStatusInputPort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/usuarios")
@RequiredArgsConstructor
@Tag(name = "Usuarios", description = "CRUD de usuarios del sistema. Requiere rol ADMIN para crear, listar y cambiar estado. ANALISTA puede consultar su propio perfil.")
public class UsuarioController {

    private final CreateUserInputPort createUserInputPort;
    private final GetUserInputPort getUserInputPort;
    private final ToggleUserStatusInputPort toggleUserStatusInputPort;
    private final ChangePasswordInputPort changePasswordInputPort;

    @PostMapping
    @Operation(
            summary = "Crear usuario",
            description = "Registra un nuevo usuario con rol asignado. Solo ADMIN."
    )
    @ApiResponse(responseCode = "201", description = "Usuario creado exitosamente")
    @ApiResponse(responseCode = "400", description = "Datos de entrada inválidos",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "409", description = "Email ya registrado",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        UserResponse response = createUserInputPort.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(
            summary = "Listar usuarios",
            description = "Retorna todos los usuarios del sistema. Solo ADMIN."
    )
    @ApiResponse(responseCode = "200", description = "Lista de usuarios")
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        return ResponseEntity.ok(getUserInputPort.getAllUsers());
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Consultar usuario por ID",
            description = "ADMIN puede consultar cualquier usuario. ANALISTA solo su propio perfil."
    )
    @ApiResponse(responseCode = "200", description = "Detalle del usuario")
    @ApiResponse(responseCode = "403", description = "ANALISTA intentó consultar otro perfil",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "Usuario no encontrado",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ResponseEntity<UserResponse> getUserById(@PathVariable Long id, Authentication authentication) {
        UserResponse user = getUserInputPort.getUserById(id);

        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        if (!isAdmin && !user.getEmail().equals(authentication.getName())) {
            throw new AccessDeniedException("Solo puede consultar su propio perfil");
        }

        return ResponseEntity.ok(user);
    }

    @PatchMapping("/{id}/estado")
    @Operation(
            summary = "Activar o desactivar usuario",
            description = "Cambia el estado activo/inactivo de un usuario. Solo ADMIN."
    )
    @ApiResponse(responseCode = "200", description = "Estado actualizado")
    @ApiResponse(responseCode = "404", description = "Usuario no encontrado",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ResponseEntity<UserResponse> toggleStatus(
            @PathVariable Long id,
            @Valid @RequestBody ToggleStatusRequest request) {
        return ResponseEntity.ok(toggleUserStatusInputPort.toggleStatus(id, request));
    }

    @PatchMapping("/{id}/password")
    @Operation(
            summary = "Cambiar contrasena",
            description = "ADMIN puede cambiar la contrasena de cualquier usuario (reset). ANALISTA solo la propia, verificando la contrasena actual."
    )
    @ApiResponse(responseCode = "204", description = "Contrasena actualizada")
    @ApiResponse(responseCode = "401", description = "Contrasena actual incorrecta",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "403", description = "ANALISTA intento cambiar contrasena de otro usuario",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    @ApiResponse(responseCode = "404", description = "Usuario no encontrado",
            content = @Content(schema = @Schema(implementation = ApiError.class)))
    public ResponseEntity<Void> changePassword(
            @PathVariable Long id,
            @Valid @RequestBody ChangePasswordRequest request,
            Authentication authentication) {

        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        changePasswordInputPort.changePassword(id, request, authentication.getName(), isAdmin);
        return ResponseEntity.noContent().build();
    }
}
