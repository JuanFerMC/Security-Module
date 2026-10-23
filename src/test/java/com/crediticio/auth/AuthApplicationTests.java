package com.crediticio.auth;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(initializers = TestRsaKeyInitializer.class)
class AuthApplicationTests {

    @Test
    void contextLoads() {
    }
}
