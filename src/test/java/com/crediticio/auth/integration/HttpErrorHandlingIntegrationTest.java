package com.crediticio.auth.integration;

import com.crediticio.auth.authentication.infrastructure.output.RefreshTokenJpaRepository;
import com.crediticio.auth.authentication.ports.output.PasswordEncoderPort;
import com.crediticio.auth.roles.infrastructure.output.RolJpaEntity;
import com.crediticio.auth.roles.infrastructure.output.RolJpaRepository;
import com.crediticio.auth.users.infrastructure.output.UsuarioJpaEntity;
import com.crediticio.auth.users.infrastructure.output.UsuarioJpaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import com.crediticio.auth.TestRsaKeyInitializer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas contra un servidor HTTP real (Tomcat embebido en puerto aleatorio).
 *
 * <p>MockMvc no reproduce el despacho de errores del contenedor (/error) ni la codificacion real de las
 * respuestas, por lo que estos casos no se detectaban con {@code @AutoConfigureMockMvc}.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:errtestdb;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestRsaKeyInitializer.class)
class HttpErrorHandlingIntegrationTest {

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
        RolJpaEntity analista = rolRepository.save(new RolJpaEntity(null, "ANALISTA_CREDITO"));
        crearUsuario("admin@test.com", admin);
        crearUsuario("analista@test.com", analista);
    }

    private void crearUsuario(String email, RolJpaEntity rol) {
        UsuarioJpaEntity u = new UsuarioJpaEntity();
        u.setNombre(email);
        u.setEmail(email);
        u.setPasswordHash(passwordEncoder.encode(PASSWORD));
        u.setRol(rol);
        u.setActivo(true);
        usuarioRepository.save(u);
    }

    private HttpResponse<byte[]> send(String method, String path, String body, String bearer) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json");
        if (bearer != null) {
            builder.header("Authorization", "Bearer " + bearer);
        }
        builder.method(method, body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private String accessToken(String email) throws Exception {
        HttpResponse<byte[]> r = send("POST", "/api/v1/auth/login",
                "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}", null);
        assertEquals(200, r.statusCode());
        return objectMapper.readTree(r.body()).get("token").asText();
    }

    private JsonNode json(HttpResponse<byte[]> r) throws Exception {
        return objectMapper.readTree(new String(r.body(), StandardCharsets.UTF_8));
    }

    private String contentType(HttpResponse<byte[]> r) {
        return r.headers().firstValue("Content-Type").orElse("").toLowerCase();
    }

    // ---------- H1: errores de entrada deben ser 400, no 401 ----------

    @Test
    void malformedJson_onPublicEndpoint_returns400() throws Exception {
        HttpResponse<byte[]> r = send("POST", "/api/v1/auth/login", "{\"email\": ", null);

        assertEquals(400, r.statusCode());
        assertEquals("MALFORMED_REQUEST", json(r).get("errorCode").asText());
    }

    @Test
    void malformedJson_onProtectedEndpoint_returns400() throws Exception {
        HttpResponse<byte[]> r = send("POST", "/api/v1/usuarios", "{\"nombre\": ", accessToken("admin@test.com"));

        assertEquals(400, r.statusCode());
        assertEquals("MALFORMED_REQUEST", json(r).get("errorCode").asText());
    }

    @Test
    void wrongFieldType_returns400() throws Exception {
        HttpResponse<byte[]> r = send("POST", "/api/v1/usuarios",
                "{\"nombre\":\"X\",\"email\":\"x@test.com\",\"password\":\"" + PASSWORD + "\",\"idRol\":\"abc\"}",
                accessToken("admin@test.com"));

        assertEquals(400, r.statusCode());
    }

    @Test
    void nonNumericPathVariable_returns400() throws Exception {
        HttpResponse<byte[]> r = send("GET", "/api/v1/usuarios/abc", null, accessToken("admin@test.com"));

        assertEquals(400, r.statusCode());
        assertEquals("INVALID_PARAMETER", json(r).get("errorCode").asText());
    }

    @Test
    void unauthenticatedRequest_stillReturns401() throws Exception {
        assertEquals(401, send("GET", "/api/v1/usuarios", null, null).statusCode());
    }

    // ---------- H2: UTF-8 en 401/403 ----------

    @Test
    void unauthorizedResponse_isUtf8() throws Exception {
        HttpResponse<byte[]> r = send("GET", "/api/v1/usuarios", null, null);

        assertEquals(401, r.statusCode());
        assertTrue(contentType(r).contains("charset=utf-8"), "Content-Type: " + contentType(r));
        String cuerpo = new String(r.body(), StandardCharsets.UTF_8);
        assertTrue(cuerpo.contains("Autenticación requerida"), cuerpo);
        assertTrue(!cuerpo.contains("�"), "el cuerpo contiene caracteres de reemplazo");
    }

    @Test
    void forbiddenResponse_isUtf8() throws Exception {
        HttpResponse<byte[]> r = send("GET", "/api/v1/usuarios", null, accessToken("analista@test.com"));

        assertEquals(403, r.statusCode());
        assertTrue(contentType(r).contains("charset=utf-8"), "Content-Type: " + contentType(r));
        assertEquals("ACCESS_DENIED", json(r).get("errorCode").asText());
    }

    // ---------- H3: idRol inexistente ----------

    @Test
    void unknownRoleId_returns400() throws Exception {
        HttpResponse<byte[]> r = send("POST", "/api/v1/usuarios",
                "{\"nombre\":\"X\",\"email\":\"x@test.com\",\"password\":\"" + PASSWORD + "\",\"idRol\":99999}",
                accessToken("admin@test.com"));

        assertEquals(400, r.statusCode());
        assertEquals("ROL_NOT_FOUND", json(r).get("errorCode").asText());
    }

    // ---------- H4: refresh token invalido siempre es 401 ----------

    @Test
    void refresh_withEmptyBody_returns401() throws Exception {
        assertEquals(401, send("POST", "/api/v1/auth/refresh", "{}", null).statusCode());
    }

    @Test
    void refresh_withBlankToken_returns401() throws Exception {
        assertEquals(401, send("POST", "/api/v1/auth/refresh", "{\"refreshToken\":\"\"}", null).statusCode());
    }

    @Test
    void refresh_withOversizedToken_returns401() throws Exception {
        String largo = "a".repeat(600);

        HttpResponse<byte[]> r = send("POST", "/api/v1/auth/refresh", "{\"refreshToken\":\"" + largo + "\"}", null);

        assertEquals(401, r.statusCode());
        assertEquals("INVALID_CREDENTIALS", json(r).get("errorCode").asText());
    }

    @Test
    void logout_withInvalidToken_isIdempotent() throws Exception {
        String largo = "a".repeat(600);

        assertEquals(204, send("POST", "/api/v1/auth/logout", "{\"refreshToken\":\"" + largo + "\"}", null).statusCode());
        assertEquals(204, send("POST", "/api/v1/auth/logout", "{}", null).statusCode());
    }
}
