-- V111: Envers necesita una columna por cada propiedad auditada de UsuarioJpaEntity.
-- V109 agrego uuid_publico a usuarios, pero no a su tabla de revisiones.
-- Se permite NULL para conservar las revisiones historicas creadas antes de V109.
ALTER TABLE usuarios_aud
    ADD COLUMN uuid_publico UUID;
