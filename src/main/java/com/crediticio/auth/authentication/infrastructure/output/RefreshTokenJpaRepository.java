package com.crediticio.auth.authentication.infrastructure.output;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenJpaRepository extends JpaRepository<RefreshTokenJpaEntity, Long> {

    Optional<RefreshTokenJpaEntity> findByTokenHash(String tokenHash);

    // UPDATE condicional: la base de datos serializa los accesos a la fila, de modo que de dos
    // renovaciones concurrentes solo una encuentra usado_en/revocado_en nulos y actualiza 1 fila.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RefreshTokenJpaEntity t set t.usadoEn = :ahora "
            + "where t.id = :id and t.usadoEn is null and t.revocadoEn is null")
    int marcarUsado(@Param("id") Long id, @Param("ahora") LocalDateTime ahora);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RefreshTokenJpaEntity t set t.revocadoEn = :ahora "
            + "where t.familiaId = :familiaId and t.revocadoEn is null")
    int revocarFamilia(@Param("familiaId") UUID familiaId, @Param("ahora") LocalDateTime ahora);
}
