package com.crediticio.auth;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

public class TestRsaKeyInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static Path tempDir;
    private static volatile boolean initialized = false;

    @Override
    public void initialize(ConfigurableApplicationContext ctx) {
        try {
            ensureKeys();
            ctx.getEnvironment().getSystemProperties()
                    .put("app.jwt.private-key", "file:" + tempDir.resolve("jwt-privada-test.pem"));
            ctx.getEnvironment().getSystemProperties()
                    .put("app.jwt.public-key", "file:" + tempDir.resolve("jwt-publica-test.pem"));
        } catch (Exception e) {
            throw new RuntimeException("No se pudieron generar las claves RSA de test", e);
        }
    }

    public static synchronized void ensureKeys() throws NoSuchAlgorithmException, IOException {
        if (initialized) return;
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair pair = gen.generateKeyPair();

        tempDir = Files.createTempDirectory("auth-test-keys-");
        tempDir.toFile().deleteOnExit();

        Path privPath = tempDir.resolve("jwt-privada-test.pem");
        Path pubPath = tempDir.resolve("jwt-publica-test.pem");

        Files.writeString(privPath, toPkcs8Pem(pair.getPrivate().getEncoded()));
        Files.writeString(pubPath, toX509Pem(pair.getPublic().getEncoded()));

        privPath.toFile().deleteOnExit();
        pubPath.toFile().deleteOnExit();

        initialized = true;
    }

    public static Path getTempDir() {
        return tempDir;
    }

    private static String toPkcs8Pem(byte[] der) {
        return "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(der)
                + "\n-----END PRIVATE KEY-----\n";
    }

    private static String toX509Pem(byte[] der) {
        return "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(der)
                + "\n-----END PUBLIC KEY-----\n";
    }
}
