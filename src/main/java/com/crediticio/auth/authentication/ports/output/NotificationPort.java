package com.crediticio.auth.authentication.ports.output;

public interface NotificationPort {

    void sendPasswordResetEmail(String email, String resetToken);
}
