package com.crediticio.auth.integration;

import com.crediticio.auth.TestRsaKeyInitializer;
import com.crediticio.auth.authentication.infrastructure.output.RefreshTokenJpaRepository;
import com.crediticio.auth.authentication.ports.output.PasswordEncoderPort;
import com.crediticio.auth.roles.infrastructure.output.RolJpaEntity;
import com.crediticio.auth.roles.infrastructure.output.RolJpaRepository;
import com.crediticio.auth.users.infrastructure.output.UsuarioJpaEntity;
import com.crediticio.auth.users.infrastructure.output.UsuarioJpaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fase 4: /actuator/prometheus con las métricas habilitadas, contra un servidor HTTP real. Solo la cuenta técnica
 * Basic del scraper lee las métricas; un JWT de usuario (incluso de administrador) no sirve, y la cuenta técnica
 * no da acceso a la API. El caso con las métricas apagadas está en HttpErrorHandlingIntegrationTest.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:metricasdb;DB_CLOSE_DELAY=-1",
                // Las pruebas de Spring Boot desactivan por defecto la exportación de métricas: se habilita Prometheus.
                "management.prometheus.metrics.export.enabled=true",
                "app.metrics.enabled=true",
                "app.metrics.username=prometheus-test",
                "app.metrics.password=prometheus-test-password"
        })
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestRsaKeyInitializer.class)
class MetricasPrometheusIntegrationTest {

    private static final String PASSWORD = "claveSegura2026!";

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private RolJpaRepository rolRepository;
    @Autowired
    private UsuarioJpaRepository usuarioRepository;
    @Autowired
    private RefreshTokenJpaRepository refreshTokenRepository;
    @Autowired
    private PasswordEncoderPort passwordEncoder;

    private final HttpClient client = HttpClient.newHttpClient();

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        usuarioRepository.deleteAll();
        rolRepository.deleteAll();
        RolJpaEntity admin = rolRepository.save(new RolJpaEntity(null, "ADMINISTRADOR_RIESGO"));
        UsuarioJpaEntity u = new UsuarioJpaEntity();
        u.setNombre("admin@test.com");
        u.setEmail("admin@test.com");
        u.setPasswordHash(passwordEncoder.encode(PASSWORD));
        u.setRol(admin);
        u.setActivo(true);
        usuarioRepository.save(u);
    }

    private HttpResponse<String> send(String method, String path, String authorization) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .method(method, HttpRequest.BodyPublishers.noBody());
        if (authorization != null) {
            builder.header("Authorization", authorization);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String basic(String usuario, String clave) {
        return "Basic " + Base64.getEncoder().encodeToString((usuario + ":" + clave).getBytes(StandardCharsets.UTF_8));
    }

    private String bearerAdmin() throws Exception {
        HttpRequest login = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"email\":\"admin@test.com\",\"password\":\"" + PASSWORD + "\"}", StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> r = client.send(login, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, r.statusCode());
        return "Bearer " + objectMapper.readTree(r.body()).get("token").asText();
    }

    @Test
    void prometheus_rejectsAnonymousAccess() throws Exception {
        assertEquals(401, send("GET", "/actuator/prometheus", null).statusCode());
    }

    @Test
    void prometheus_acceptsTechnicalAccountAndReturnsRealMetrics() throws Exception {
        HttpResponse<String> r = send("GET", "/actuator/prometheus", basic("prometheus-test", "prometheus-test-password"));

        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("# HELP"), r.body());
        assertTrue(r.body().contains("jvm_memory_used_bytes"), "faltan métricas JVM");
    }

    @Test
    void prometheus_rejectsWrongBasicCredentials() throws Exception {
        assertEquals(401, send("GET", "/actuator/prometheus", basic("prometheus-test", "otra-clave")).statusCode());
        assertEquals(401, send("GET", "/actuator/prometheus", basic("otro", "prometheus-test-password")).statusCode());
    }

    @Test
    void prometheus_doesNotAcceptUserJwt() throws Exception {
        assertEquals(401, send("GET", "/actuator/prometheus", bearerAdmin()).statusCode());
    }

    @Test
    void prometheus_deniesMethodsOtherThanGet() throws Exception {
        assertEquals(403, send("POST", "/actuator/prometheus", basic("prometheus-test", "prometheus-test-password"))
                .statusCode());
    }

    @Test
    void technicalAccount_doesNotGrantApiAccess() throws Exception {
        assertEquals(401, send("GET", "/api/v1/usuarios", basic("prometheus-test", "prometheus-test-password"))
                .statusCode());
    }
}
