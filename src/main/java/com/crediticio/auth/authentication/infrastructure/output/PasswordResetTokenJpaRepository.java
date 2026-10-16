package com.crediticio.auth.authentication.infrastructure.output;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PasswordResetTokenJpaRepository extends JpaRepository<PasswordResetTokenJpaEntity, Long> {

    Optional<PasswordResetTokenJpaEntity> findByToken(String token);

    void deleteByEmail(String email);
}
