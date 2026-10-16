package com.crediticio.auth.authentication.infrastructure.output;

import com.crediticio.auth.authentication.domain.PasswordResetToken;
import com.crediticio.auth.authentication.ports.output.PasswordResetTokenRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PasswordResetTokenRepositoryAdapter implements PasswordResetTokenRepositoryPort {

    private final PasswordResetTokenJpaRepository jpaRepository;

    @Override
    public PasswordResetToken save(PasswordResetToken token) {
        PasswordResetTokenJpaEntity entity = PasswordResetTokenJpaEntity.builder()
                .id(token.getId())
                .token(token.getToken())
                .email(token.getEmail())
                .expiresAt(token.getExpiresAt())
                .used(token.isUsed())
                .build();

        PasswordResetTokenJpaEntity saved = jpaRepository.save(entity);

        return PasswordResetToken.builder()
                .id(saved.getId())
                .token(saved.getToken())
                .email(saved.getEmail())
                .expiresAt(saved.getExpiresAt())
                .used(saved.isUsed())
                .build();
    }

    @Override
    public Optional<PasswordResetToken> findByToken(String token) {
        return jpaRepository.findByToken(token)
                .map(entity -> PasswordResetToken.builder()
                        .id(entity.getId())
                        .token(entity.getToken())
                        .email(entity.getEmail())
                        .expiresAt(entity.getExpiresAt())
                        .used(entity.isUsed())
                        .build());
    }

    @Override
    public void deleteByEmail(String email) {
        jpaRepository.deleteByEmail(email);
    }
}
