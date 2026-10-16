package com.crediticio.auth.authentication.infrastructure.output;

import com.crediticio.auth.authentication.ports.output.NotificationPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ConsoleNotificationAdapter implements NotificationPort {

    @Override
    public void sendPasswordResetEmail(String email, String resetToken) {
        log.info("========================================");
        log.info("  RECUPERACION DE CONTRASENA");
        log.info("  Para: {}", email);
        log.info("  Token: {}", resetToken);
        log.info("  Expira en: 30 minutos");
        log.info("========================================");
    }
}
