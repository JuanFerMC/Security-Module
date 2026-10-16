package com.crediticio.auth.authentication.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class PasswordResetToken {

    private Long id;
    private String token;
    private String email;
    private LocalDateTime expiresAt;
    private boolean used;

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    public boolean isValid() {
        return !used && !isExpired();
    }
}
