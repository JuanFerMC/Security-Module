-- HU08: roles definidos por la HU, correo normalizado e indice unico sin distinguir
-- mayusculas, actor que creo el usuario, y retiro del seed con credencial publica.

-- RF06: nombres de rol de la HU (las filas conservan su id; no se tocan las FK).
UPDATE roles SET nombre = 'ADMINISTRADOR_RIESGO' WHERE nombre = 'ADMIN';
UPDATE roles SET nombre = 'ANALISTA_CREDITO'     WHERE nombre = 'ANALISTA';

-- RF02/RF03: correo normalizado y unicidad garantizada en BD sin distinguir mayusculas.
UPDATE usuarios SET email = lower(btrim(email));
CREATE UNIQUE INDEX uq_usuarios_email_lower ON usuarios (lower(email));

-- RNF05: actor que realizo la operacion de creacion.
ALTER TABLE usuarios     ADD COLUMN creado_por VARCHAR(255);
ALTER TABLE usuarios_aud ADD COLUMN creado_por VARCHAR(255);

-- RF09/RNF06: el seed V105 publicaba una credencial conocida. Se elimina SOLO si sigue
-- intacto (mismo hash del seed, sin cambio de contrasena). El primer administrador se
-- crea ahora con INIT_ADMIN_EMAIL / INIT_ADMIN_PASSWORD (ver README).
DELETE FROM usuarios
 WHERE email = 'admin@crediticio.com'
   AND password_hash = '$2a$10$dWbPtOZ7a2sjWm7WNiMEte6yddSJyiji5u.15UwPnxeQCW1vhEjYO';
