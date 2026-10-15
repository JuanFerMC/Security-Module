package com.crediticio.auth.roles.infrastructure.output;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.envers.Audited;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "roles")
@Audited
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RolJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_rol")
    // La columna es SERIAL (INTEGER) en BD (V100); Long en Java. Sin esto Hibernate espera BIGINT y falla la validacion.
    @JdbcTypeCode(SqlTypes.INTEGER)
    private Long id;

    @Column(name = "nombre", unique = true, nullable = false, length = 50)
    private String nombre;
}
