-- Tabla para tokens de recuperacion de contrasena (un solo uso, 30 min de expiracion)
CREATE TABLE password_reset_tokens (
    id          SERIAL PRIMARY KEY,
    token       VARCHAR(255) NOT NULL UNIQUE,
    email       VARCHAR(255) NOT NULL,
    expires_at  TIMESTAMP    NOT NULL,
    used        BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_password_reset_tokens_email ON password_reset_tokens (email);
CREATE INDEX idx_password_reset_tokens_token ON password_reset_tokens (token);
