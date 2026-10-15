-- HU09: refresh tokens persistidos solo como hash (RF07), con familia de sesion para
-- rotacion y deteccion de reutilizacion (RF09/RF10).
--   usado_en    : el token ya fue canjeado por uno nuevo (rotado).
--   revocado_en : el token (o toda su familia) fue invalidado: logout o reutilizacion.
CREATE TABLE refresh_tokens (
    id           BIGSERIAL    PRIMARY KEY,
    usuario_id   INTEGER      NOT NULL REFERENCES usuarios(id_usuario),
    familia_id   UUID         NOT NULL,
    token_hash   VARCHAR(64)  NOT NULL,
    emitido_en   TIMESTAMP    NOT NULL,
    expira_en    TIMESTAMP    NOT NULL,
    usado_en     TIMESTAMP,
    revocado_en  TIMESTAMP
);

CREATE UNIQUE INDEX uq_refresh_tokens_hash ON refresh_tokens (token_hash);
CREATE INDEX idx_refresh_tokens_familia ON refresh_tokens (familia_id);
CREATE INDEX idx_refresh_tokens_usuario ON refresh_tokens (usuario_id);
