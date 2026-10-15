package com.crediticio.auth.authentication.infrastructure.output;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
public class RefreshTokenJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // usuarios.id_usuario es SERIAL (INTEGER); ver nota en UsuarioJpaEntity.
    @JdbcTypeCode(SqlTypes.INTEGER)
    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "familia_id", nullable = false)
    private UUID familiaId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "emitido_en", nullable = false)
    private LocalDateTime emitidoEn;

    @Column(name = "expira_en", nullable = false)
    private LocalDateTime expiraEn;

    @Column(name = "usado_en")
    private LocalDateTime usadoEn;

    @Column(name = "revocado_en")
    private LocalDateTime revocadoEn;
}
