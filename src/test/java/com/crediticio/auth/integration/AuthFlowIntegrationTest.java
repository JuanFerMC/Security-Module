package com.crediticio.auth.integration;

import com.crediticio.auth.authentication.ports.output.PasswordEncoderPort;
import com.crediticio.auth.roles.infrastructure.output.RolJpaEntity;
import com.crediticio.auth.roles.infrastructure.output.RolJpaRepository;
import com.crediticio.auth.users.infrastructure.output.UsuarioJpaEntity;
import com.crediticio.auth.users.infrastructure.output.UsuarioJpaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class AuthFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RolJpaRepository rolRepository;

    @Autowired
    private UsuarioJpaRepository usuarioRepository;

    @Autowired
    private PasswordEncoderPort passwordEncoder;

    @Autowired
    private com.crediticio.auth.authentication.infrastructure.output.RefreshTokenJpaRepository refreshTokenRepository;

    private RolJpaEntity adminRol;
    private RolJpaEntity analistaRol;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        usuarioRepository.deleteAll();
        rolRepository.deleteAll();

        adminRol = rolRepository.save(new RolJpaEntity(null, "ADMINISTRADOR_RIESGO"));
        analistaRol = rolRepository.save(new RolJpaEntity(null, "ANALISTA_CREDITO"));

        UsuarioJpaEntity admin = new UsuarioJpaEntity();
        admin.setNombre("Admin Test");
        admin.setEmail("admin@test.com");
        admin.setPasswordHash(passwordEncoder.encode("admin123"));
        admin.setRol(adminRol);
        admin.setActivo(true);
        usuarioRepository.save(admin);

        UsuarioJpaEntity analista = new UsuarioJpaEntity();
        analista.setNombre("Analista Test");
        analista.setEmail("analista@test.com");
        analista.setPasswordHash(passwordEncoder.encode("analista123"));
        analista.setRol(analistaRol);
        analista.setActivo(true);
        usuarioRepository.save(analista);
    }

    private String loginAndGetToken(String email, String password) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(response).get("token").asText();
    }

    private JsonNode login(String email, String password) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    private String loginAndGetRefresh(String email, String password) throws Exception {
        return login(email, password).get("refreshToken").asText();
    }

    private org.springframework.test.web.servlet.ResultActions refreshWith(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\"}"));
    }

    @Nested
    class LoginTests {

        @Test
        void login_success_returnsTokenAndRol() throws Exception {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"admin@test.com\",\"password\":\"admin123\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").isNotEmpty())
                    .andExpect(jsonPath("$.rol").value("ADMINISTRADOR_RIESGO"));
        }

        @Test
        void login_wrongPassword_returns401() throws Exception {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"admin@test.com\",\"password\":\"wrong\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"));
        }

        @Test
        void login_nonexistentUser_returns401() throws Exception {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"noexiste@test.com\",\"password\":\"pass\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"));
        }

        @Test
        void login_invalidRequest_returns400() throws Exception {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"\",\"password\":\"\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
        }
    }

    @Nested
    class UserCrudTests {

        @Test
        void createUser_asAdmin_returns201() throws Exception {
            String token = loginAndGetToken("admin@test.com", "admin123");

            mockMvc.perform(post("/api/v1/usuarios")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"nombre\":\"Nuevo\",\"email\":\"nuevo@test.com\",\"password\":\"passwordSegura123\",\"idRol\":" + analistaRol.getId() + "}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.nombre").value("Nuevo"))
                    .andExpect(jsonPath("$.email").value("nuevo@test.com"))
                    .andExpect(jsonPath("$.rolNombre").value("ANALISTA_CREDITO"))
                    .andExpect(jsonPath("$.activo").value(true))
                    .andExpect(jsonPath("$.passwordHash").doesNotExist());
        }

        @Test
        void createUser_asAnalista_returns403() throws Exception {
            String token = loginAndGetToken("analista@test.com", "analista123");

            mockMvc.perform(post("/api/v1/usuarios")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"nombre\":\"X\",\"email\":\"x@test.com\",\"password\":\"passwordSegura123\",\"idRol\":1}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        void createUser_unauthenticated_returns401() throws Exception {
            mockMvc.perform(post("/api/v1/usuarios")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"nombre\":\"X\",\"email\":\"x@test.com\",\"password\":\"passwordSegura123\",\"idRol\":1}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void createUser_duplicateEmail_returns409() throws Exception {
            String token = loginAndGetToken("admin@test.com", "admin123");

            mockMvc.perform(post("/api/v1/usuarios")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"nombre\":\"Dup\",\"email\":\"admin@test.com\",\"password\":\"passwordSegura123\",\"idRol\":" + adminRol.getId() + "}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("DUPLICATE_EMAIL"));
        }

        @Test
        void createUser_duplicateEmailDifferentCase_returns409() throws Exception {
            String token = loginAndGetToken("admin@test.com", "admin123");

            mockMvc.perform(post("/api/v1/usuarios")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"nombre\":\"Dup\",\"email\":\"ADMIN@Test.com\",\"password\":\"passwordSegura123\",\"idRol\":" + adminRol.getId() + "}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("DUPLICATE_EMAIL"));
        }

        @Test
        void createUser_emailIsStoredNormalized_andLoginIsCaseInsensitive() throws Exception {
            String token = loginAndGetToken("admin@test.com", "admin123");

            mockMvc.perform(post("/api/v1/usuarios")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"nombre\":\"Mixto\",\"email\":\"Mixto@Test.COM\",\"password\":\"passwordSegura123\",\"idRol\":" + analistaRol.getId() + "}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.email").value("mixto@test.com"));

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"MIXTO@test.com\",\"password\":\"passwordSegura123\"}"))
                    .andExpect(status().isOk());

            org.junit.jupiter.api.Assertions.assertEquals("admin@test.com",
                    usuarioRepository.findByEmail("mixto@test.com").orElseThrow().getCreadoPor());
        }

        @Test
        void createUser_shortPassword_returns400() throws Exception {
            String token = loginAndGetToken("admin@test.com", "admin123");

            mockMvc.perform(post("/api/v1/usuarios")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"nombre\":\"Corta\",\"email\":\"corta@test.com\",\"password\":\"12345678901\",\"idRol\":" + analistaRol.getId() + "}"))
                    .andExpect(status().isBadRequest());

            org.junit.jupiter.api.Assertions.assertTrue(usuarioRepository.findByEmail("corta@test.com").isEmpty());
        }

        @Test
        void createUser_roleNotAllowed_returns400() throws Exception {
            RolJpaEntity otro = rolRepository.save(new RolJpaEntity(null, "SUPERUSUARIO"));
            String token = loginAndGetToken("admin@test.com", "admin123");

            mockMvc.perform(post("/api/v1/usuarios")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"nombre\":\"Otro\",\"email\":\"otro@test.com\",\"password\":\"passwordSegura123\",\"idRol\":" + otro.getId() + "}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("ROL_NOT_ALLOWED"));
        }

        @Test
        void listUsers_asAdmin_returnsList() throws Exception {
            String token = loginAndGetToken("admin@test.com", "admin123");

            mockMvc.perform(get("/api/v1/usuarios")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)));
        }

        @Test
        void listUsers_asAnalista_returns403() throws Exception {
            String token = loginAndGetToken("analista@test.com", "analista123");

            mockMvc.perform(get("/api/v1/usuarios")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
        }

        @Test
        void getUserById_asAdmin_anyUser() throws Exception {
            String token = loginAndGetToken("admin@test.com", "admin123");
            Long analistaId = usuarioRepository.findByEmail("analista@test.com").get().getId();

            mockMvc.perform(get("/api/v1/usuarios/" + analistaId)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value("analista@test.com"));
        }

        @Test
        void getUserById_asAnalista_ownProfile_success() throws Exception {
            String token = loginAndGetToken("analista@test.com", "analista123");
            Long analistaId = usuarioRepository.findByEmail("analista@test.com").get().getId();

            mockMvc.perform(get("/api/v1/usuarios/" + analistaId)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value("analista@test.com"));
        }

        @Test
        void getUserById_asAnalista_otherProfile_returns403() throws Exception {
            String token = loginAndGetToken("analista@test.com", "analista123");
            Long adminId = usuarioRepository.findByEmail("admin@test.com").get().getId();

            mockMvc.perform(get("/api/v1/usuarios/" + adminId)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
        }

        @Test
        void toggleStatus_asAdmin_deactivatesUser() throws Exception {
            String token = loginAndGetToken("admin@test.com", "admin123");
            Long analistaId = usuarioRepository.findByEmail("analista@test.com").get().getId();

            mockMvc.perform(patch("/api/v1/usuarios/" + analistaId + "/estado")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"activo\":false}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.activo").value(false));
        }

        @Test
        void toggleStatus_asAnalista_returns403() throws Exception {
            String token = loginAndGetToken("analista@test.com", "analista123");
            Long adminId = usuarioRepository.findByEmail("admin@test.com").get().getId();

            mockMvc.perform(patch("/api/v1/usuarios/" + adminId + "/estado")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"activo\":false}"))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    class ChangePasswordTests {

        @Test
        void changePassword_asAdmin_resetsAnyUser_returns204() throws Exception {
            String token = loginAndGetToken("admin@test.com", "admin123");
            Long analistaId = usuarioRepository.findByEmail("analista@test.com").get().getId();

            mockMvc.perform(patch("/api/v1/usuarios/" + analistaId + "/password")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"newPassword\":\"NuevaClave123\"}"))
                    .andExpect(status().isNoContent());

            // Verificar que la nueva contrasena funciona
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"analista@test.com\",\"password\":\"NuevaClave123\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").isNotEmpty());
        }

        @Test
        void changePassword_asAnalista_ownPassword_returns204() throws Exception {
            String token = loginAndGetToken("analista@test.com", "analista123");
            Long analistaId = usuarioRepository.findByEmail("analista@test.com").get().getId();

            mockMvc.perform(patch("/api/v1/usuarios/" + analistaId + "/password")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"currentPassword\":\"analista123\",\"newPassword\":\"CambioClave1!\"}"))
                    .andExpect(status().isNoContent());

            // Verificar que la nueva contrasena funciona
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"analista@test.com\",\"password\":\"CambioClave1!\"}"))
                    .andExpect(status().isOk());
        }

        @Test
        void changePassword_asAnalista_otherUser_returns403() throws Exception {
            String token = loginAndGetToken("analista@test.com", "analista123");
            Long adminId = usuarioRepository.findByEmail("admin@test.com").get().getId();

            mockMvc.perform(patch("/api/v1/usuarios/" + adminId + "/password")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"currentPassword\":\"analista123\",\"newPassword\":\"NuevaClave1!\"}"))
                    .andExpect(status().isForbidden());
        }

        @Test
        void changePassword_asAnalista_wrongCurrentPassword_returns401() throws Exception {
            String token = loginAndGetToken("analista@test.com", "analista123");
            Long analistaId = usuarioRepository.findByEmail("analista@test.com").get().getId();

            mockMvc.perform(patch("/api/v1/usuarios/" + analistaId + "/password")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"currentPassword\":\"claveIncorrecta\",\"newPassword\":\"NuevaClave1!\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"));
        }

        @Test
        void changePassword_unauthenticated_returns401() throws Exception {
            mockMvc.perform(patch("/api/v1/usuarios/1/password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"currentPassword\":\"x\",\"newPassword\":\"NuevaClave1!\"}"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class DeactivatedUserTests {

        @Test
        void deactivatedUser_tokenIsRejected_returns401() throws Exception {
            String adminToken = loginAndGetToken("admin@test.com", "admin123");
            String analistaToken = loginAndGetToken("analista@test.com", "analista123");
            Long analistaId = usuarioRepository.findByEmail("analista@test.com").get().getId();

            mockMvc.perform(patch("/api/v1/usuarios/" + analistaId + "/estado")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"activo\":false}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.activo").value(false));

            mockMvc.perform(get("/api/v1/auth/validate")
                            .header("Authorization", "Bearer " + analistaToken))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void deactivatedUser_cannotRefreshToken_returns401() throws Exception {
            String adminToken = loginAndGetToken("admin@test.com", "admin123");
            String analistaRefresh = loginAndGetRefresh("analista@test.com", "analista123");
            Long analistaId = usuarioRepository.findByEmail("analista@test.com").get().getId();

            mockMvc.perform(patch("/api/v1/usuarios/" + analistaId + "/estado")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"activo\":false}"))
                    .andExpect(status().isOk());

            mockMvc.perform(post("/api/v1/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"refreshToken\":\"" + analistaRefresh + "\"}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void deactivatedUser_cannotLogin_returns401() throws Exception {
            String adminToken = loginAndGetToken("admin@test.com", "admin123");
            Long analistaId = usuarioRepository.findByEmail("analista@test.com").get().getId();

            mockMvc.perform(patch("/api/v1/usuarios/" + analistaId + "/estado")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"activo\":false}"))
                    .andExpect(status().isOk());

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"analista@test.com\",\"password\":\"analista123\"}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void reactivatedUser_canUseTokenAgain() throws Exception {
            String adminToken = loginAndGetToken("admin@test.com", "admin123");
            Long analistaId = usuarioRepository.findByEmail("analista@test.com").get().getId();

            mockMvc.perform(patch("/api/v1/usuarios/" + analistaId + "/estado")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"activo\":false}"))
                    .andExpect(status().isOk());

            mockMvc.perform(patch("/api/v1/usuarios/" + analistaId + "/estado")
                            .header("Authorization", "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"activo\":true}"))
                    .andExpect(status().isOk());

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"analista@test.com\",\"password\":\"analista123\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").isNotEmpty());
        }
    }

    @Nested
    class TokenTests {

        @Test
        void login_returnsAccessAndRefreshTokens() throws Exception {
            JsonNode body = login("admin@test.com", "admin123");

            org.junit.jupiter.api.Assertions.assertFalse(body.get("token").asText().isEmpty());
            org.junit.jupiter.api.Assertions.assertFalse(body.get("refreshToken").asText().isEmpty());
            org.junit.jupiter.api.Assertions.assertEquals("Bearer", body.get("tokenType").asText());
            org.junit.jupiter.api.Assertions.assertEquals(900, body.get("expiresIn").asInt());
        }

        @Test
        void refreshTokenIsStoredOnlyAsHash() throws Exception {
            String refresh = loginAndGetRefresh("admin@test.com", "admin123");

            org.junit.jupiter.api.Assertions.assertTrue(
                    refreshTokenRepository.findAll().stream().noneMatch(t -> refresh.equals(t.getTokenHash())));
            org.junit.jupiter.api.Assertions.assertTrue(
                    refreshTokenRepository.findAll().stream().allMatch(t -> t.getTokenHash().length() == 64));
        }

        @Test
        void refresh_returnsNewAccessAndRotatedRefresh() throws Exception {
            String refresh = loginAndGetRefresh("admin@test.com", "admin123");

            String response = refreshWith(refresh)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").isNotEmpty())
                    .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                    .andExpect(jsonPath("$.rol").value("ADMINISTRADOR_RIESGO"))
                    .andReturn().getResponse().getContentAsString();

            String nuevo = objectMapper.readTree(response).get("refreshToken").asText();
            org.junit.jupiter.api.Assertions.assertNotEquals(refresh, nuevo);
        }

        @Test
        void refresh_newAccessTokenWorks() throws Exception {
            String refresh = loginAndGetRefresh("admin@test.com", "admin123");
            String response = refreshWith(refresh).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            String access = objectMapper.readTree(response).get("token").asText();

            mockMvc.perform(get("/api/v1/auth/validate").header("Authorization", "Bearer " + access))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value("admin@test.com"));
        }

        @Test
        void refresh_usedTokenIsRejected_andRevokesTheWholeSession() throws Exception {
            String primero = loginAndGetRefresh("admin@test.com", "admin123");
            String response = refreshWith(primero).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            String segundo = objectMapper.readTree(response).get("refreshToken").asText();

            // Reutilizar el token ya rotado: 401 y se invalida la familia completa (RF10)
            refreshWith(primero).andExpect(status().isUnauthorized());

            // El token nuevo de esa misma sesion tambien queda invalidado
            refreshWith(segundo).andExpect(status().isUnauthorized());
        }

        @Test
        void refresh_unknownToken_returns401() throws Exception {
            refreshWith("token-que-no-existe").andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"));
        }

        @Test
        void refresh_missingToken_returns401() throws Exception {
            mockMvc.perform(post("/api/v1/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void refresh_expiredToken_returns401() throws Exception {
            String refresh = loginAndGetRefresh("admin@test.com", "admin123");
            refreshTokenRepository.findAll().forEach(t -> {
                t.setExpiraEn(java.time.LocalDateTime.now().minusMinutes(1));
                refreshTokenRepository.save(t);
            });

            refreshWith(refresh).andExpect(status().isUnauthorized());
        }

        @Test
        void refresh_doesNotRequireAccessToken() throws Exception {
            String refresh = loginAndGetRefresh("admin@test.com", "admin123");

            // Sin encabezado Authorization (el access token pudo haber vencido)
            refreshWith(refresh).andExpect(status().isOk());
        }

        @Test
        void logout_invalidatesRefreshToken() throws Exception {
            String refresh = loginAndGetRefresh("admin@test.com", "admin123");

            mockMvc.perform(post("/api/v1/auth/logout")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"refreshToken\":\"" + refresh + "\"}"))
                    .andExpect(status().isNoContent());

            refreshWith(refresh).andExpect(status().isUnauthorized());
        }

        @Test
        void logout_isIdempotent_returns204() throws Exception {
            mockMvc.perform(post("/api/v1/auth/logout")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"refreshToken\":\"desconocido\"}"))
                    .andExpect(status().isNoContent());
        }

        @Test
        void refresh_concurrentRequestsWithSameToken_onlyOneSucceeds() throws Exception {
            String refresh = loginAndGetRefresh("admin@test.com", "admin123");
            int hilos = 6;
            java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(hilos);
            java.util.concurrent.CountDownLatch salida = new java.util.concurrent.CountDownLatch(1);
            java.util.List<java.util.concurrent.Future<Integer>> resultados = new java.util.ArrayList<>();

            for (int i = 0; i < hilos; i++) {
                resultados.add(pool.submit(() -> {
                    salida.await();
                    return refreshWith(refresh).andReturn().getResponse().getStatus();
                }));
            }
            salida.countDown();

            int ok = 0;
            int rechazados = 0;
            for (java.util.concurrent.Future<Integer> f : resultados) {
                int estado = f.get(30, java.util.concurrent.TimeUnit.SECONDS);
                if (estado == 200) {
                    ok++;
                } else if (estado == 401) {
                    rechazados++;
                }
            }
            pool.shutdown();

            org.junit.jupiter.api.Assertions.assertEquals(1, ok, "exactamente una renovacion debe tener exito");
            org.junit.jupiter.api.Assertions.assertEquals(hilos - 1, rechazados);
        }

        @Test
        void accessToken_withWrongIssuer_isRejected() throws Exception {
            String forged = io.jsonwebtoken.Jwts.builder()
                    .subject("1").issuer("otro-emisor")
                    .audience().add("crediticio-scoring").and()
                    .claim("rol", "ADMINISTRADOR_RIESGO")
                    .issuedAt(new java.util.Date())
                    .expiration(new java.util.Date(System.currentTimeMillis() + 60000))
                    .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                            "test-secret-key-for-testing-purposes-only-minimum-256-bits-long!!"
                                    .getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                    .compact();

            mockMvc.perform(get("/api/v1/usuarios").header("Authorization", "Bearer " + forged))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void accessToken_roleClaimIsNotTrusted() throws Exception {
            // Token valido de un analista pero con el claim rol adulterado a administrador:
            // el rol efectivo sale de la BD, asi que no puede listar usuarios.
            Long analistaId = usuarioRepository.findByEmail("analista@test.com").get().getId();
            String forged = io.jsonwebtoken.Jwts.builder()
                    .subject(String.valueOf(analistaId)).issuer("crediticio-auth")
                    .audience().add("crediticio-scoring").and()
                    .claim("rol", "ADMINISTRADOR_RIESGO")
                    .issuedAt(new java.util.Date())
                    .expiration(new java.util.Date(System.currentTimeMillis() + 60000))
                    .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                            "test-secret-key-for-testing-purposes-only-minimum-256-bits-long!!"
                                    .getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                    .compact();

            mockMvc.perform(get("/api/v1/usuarios").header("Authorization", "Bearer " + forged))
                    .andExpect(status().isForbidden());
        }

        @Test
        void validate_withoutToken_returns401() throws Exception {
            mockMvc.perform(get("/api/v1/auth/validate"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void login_failuresDoNotRevealWhichCredentialFailed() throws Exception {
            String desconocido = mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"noexiste@test.com\",\"password\":\"cualquiera\"}"))
                    .andExpect(status().isUnauthorized())
                    .andReturn().getResponse().getContentAsString();
            String mala = mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"admin@test.com\",\"password\":\"incorrecta\"}"))
                    .andExpect(status().isUnauthorized())
                    .andReturn().getResponse().getContentAsString();

            org.junit.jupiter.api.Assertions.assertEquals(
                    objectMapper.readTree(desconocido).get("message").asText(),
                    objectMapper.readTree(mala).get("message").asText());
        }
    }
}
