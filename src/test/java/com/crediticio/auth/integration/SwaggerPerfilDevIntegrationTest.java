package com.crediticio.auth.integration;

import com.crediticio.auth.TestRsaKeyInitializer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Con el perfil "dev" la documentacion OpenAPI sigue disponible para desarrollo local. Fuera de ese perfil
 * queda deshabilitada (ver HttpErrorHandlingIntegrationTest#swagger_isDisabledOutsideDevProfile).
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:swaggerdevdb;DB_CLOSE_DELAY=-1")
@ActiveProfiles({"test", "dev"})
@ContextConfiguration(initializers = TestRsaKeyInitializer.class)
class SwaggerPerfilDevIntegrationTest {

    @LocalServerPort
    private int port;

    @Test
    void apiDocs_isAvailableWithDevProfile() throws Exception {
        HttpResponse<String> r = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v3/api-docs")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("\"openapi\""), r.body());
    }
}
