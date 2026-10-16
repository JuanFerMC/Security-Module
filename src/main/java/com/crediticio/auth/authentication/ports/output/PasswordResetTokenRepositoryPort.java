package com.crediticio.auth.authentication.ports.output;

import com.crediticio.auth.authentication.domain.PasswordResetToken;

import java.util.Optional;

public interface PasswordResetTokenRepositoryPort {

    PasswordResetToken save(PasswordResetToken token);

    Optional<PasswordResetToken> findByToken(String token);

    void deleteByEmail(String email);
}
