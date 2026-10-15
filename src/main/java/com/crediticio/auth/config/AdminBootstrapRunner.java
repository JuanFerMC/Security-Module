package com.crediticio.auth.config;

import com.crediticio.auth.authentication.ports.output.PasswordEncoderPort;
import com.crediticio.auth.roles.domain.Rol;
import com.crediticio.auth.roles.domain.RolSistema;
import com.crediticio.auth.roles.ports.output.RolRepositoryPort;
import com.crediticio.auth.shared.EmailNormalizer;
import com.crediticio.auth.users.domain.Usuario;
import com.crediticio.auth.users.ports.output.UserRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inicializacion controlada del primer administrador (HU08 RF09/RF10).
 *
 * <p>Fuera de la API publica: solo se ejecuta al arrancar, solo si se definieron
 * {@code INIT_ADMIN_EMAIL} e {@code INIT_ADMIN_PASSWORD}, y solo si todavia no existe
 * ningun usuario con rol ADMINISTRADOR_RIESGO. Una vez creado el primer administrador la
 * inicializacion queda deshabilitada por esa misma comprobacion. Nunca registra la contrasena.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final int PASSWORD_MIN_LENGTH = 12;
    private static final String ACTOR = "BOOTSTRAP";

    private final UserRepositoryPort userRepository;
    private final RolRepositoryPort rolRepository;
    private final PasswordEncoderPort passwordEncoder;

    @Value("${app.bootstrap.admin-email:}")
    private String adminEmail;

    @Value("${app.bootstrap.admin-password:}")
    private String adminPassword;

    @Value("${app.bootstrap.admin-nombre:Administrador}")
    private String adminNombre;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        boolean configurado = !adminEmail.isBlank() && !adminPassword.isBlank();

        if (userRepository.existsByRolNombre(RolSistema.ADMINISTRADOR_RIESGO)) {
            if (configurado) {
                log.warn("Inicializacion de administrador ignorada: ya existe un {}. "
                        + "Puede retirar INIT_ADMIN_EMAIL e INIT_ADMIN_PASSWORD.", RolSistema.ADMINISTRADOR_RIESGO);
            }
            return;
        }

        if (!configurado) {
            log.warn("No existe ningun {} y no se definieron INIT_ADMIN_EMAIL / INIT_ADMIN_PASSWORD: "
                    + "nadie podra crear usuarios hasta inicializar el primer administrador.",
                    RolSistema.ADMINISTRADOR_RIESGO);
            return;
        }

        if (adminPassword.length() < PASSWORD_MIN_LENGTH) {
            throw new IllegalStateException(
                    "INIT_ADMIN_PASSWORD no cumple la politica: minimo " + PASSWORD_MIN_LENGTH + " caracteres");
        }

        Rol rol = rolRepository.findByNombre(RolSistema.ADMINISTRADOR_RIESGO)
                .orElseThrow(() -> new IllegalStateException(
                        "No existe el rol " + RolSistema.ADMINISTRADOR_RIESGO + "; revise las migraciones"));

        String email = EmailNormalizer.normalize(adminEmail);
        if (userRepository.existsByEmail(email)) {
            throw new IllegalStateException(
                    "INIT_ADMIN_EMAIL ya pertenece a un usuario que no es " + RolSistema.ADMINISTRADOR_RIESGO);
        }

        Usuario admin = Usuario.builder()
                .nombre(adminNombre)
                .email(email)
                .passwordHash(passwordEncoder.encode(adminPassword))
                .rol(rol)
                .activo(true)
                .creadoPor(ACTOR)
                .build();
        Usuario saved = userRepository.save(admin);

        log.info("Primer administrador inicializado: id={}, rol={}, actor={}",
                saved.getId(), RolSistema.ADMINISTRADOR_RIESGO, ACTOR);
    }
}
