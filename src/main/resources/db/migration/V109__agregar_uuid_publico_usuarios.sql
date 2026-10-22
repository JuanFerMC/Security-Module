-- V109: Agregar UUID público a usuarios para uso como sub en JWT RS256.
-- scoring-api y evaluation-query-api validan que sub sea UUID; el id SERIAL interno no sale del auth-api.
ALTER TABLE usuarios ADD COLUMN uuid_publico UUID NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE usuarios ADD CONSTRAINT uq_usuarios_uuid_publico UNIQUE (uuid_publico);
