package com.crediticio.auth.authentication.infrastructure.output;

import com.crediticio.auth.authentication.domain.RefreshToken;
import com.crediticio.auth.authentication.ports.output.RefreshTokenRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RefreshTokenJpaAdapter implements RefreshTokenRepositoryPort {

    private final RefreshTokenJpaRepository repository;

    @Override
    public RefreshToken save(RefreshToken refreshToken) {
        RefreshTokenJpaEntity entity = new RefreshTokenJpaEntity();
        entity.setId(refreshToken.getId());
        entity.setUsuarioId(refreshToken.getUsuarioId());
        entity.setFamiliaId(refreshToken.getFamiliaId());
        entity.setTokenHash(refreshToken.getTokenHash());
        entity.setEmitidoEn(refreshToken.getEmitidoEn());
        entity.setExpiraEn(refreshToken.getExpiraEn());
        entity.setUsadoEn(refreshToken.getUsadoEn());
        entity.setRevocadoEn(refreshToken.getRevocadoEn());
        return toDomain(repository.save(entity));
    }

    @Override
    public Optional<RefreshToken> findByTokenHash(String tokenHash) {
        return repository.findByTokenHash(tokenHash).map(this::toDomain);
    }

    @Override
    public boolean marcarUsado(Long id, LocalDateTime ahora) {
        return repository.marcarUsado(id, ahora) == 1;
    }

    @Override
    public int revocarFamilia(UUID familiaId, LocalDateTime ahora) {
        return repository.revocarFamilia(familiaId, ahora);
    }

    private RefreshToken toDomain(RefreshTokenJpaEntity e) {
        return RefreshToken.builder()
                .id(e.getId())
                .usuarioId(e.getUsuarioId())
                .familiaId(e.getFamiliaId())
                .tokenHash(e.getTokenHash())
                .emitidoEn(e.getEmitidoEn())
                .expiraEn(e.getExpiraEn())
                .usadoEn(e.getUsadoEn())
                .revocadoEn(e.getRevocadoEn())
                .build();
    }
}
