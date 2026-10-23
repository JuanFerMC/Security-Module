-- Corrige el tipo de password_reset_tokens.id: V108 lo creo como SERIAL (INTEGER)
-- pero la entidad JPA usa Long (BIGINT). Hibernate ddl-auto:validate falla por el desajuste.
ALTER TABLE password_reset_tokens
    ALTER COLUMN id SET DATA TYPE BIGINT;

-- Cambiar la secuencia asociada para que tambien produzca BIGINT.
ALTER SEQUENCE password_reset_tokens_id_seq AS BIGINT;
