-- Usuario administrador inicial (seed).
-- Contrasena: Admin2026! (hash BCrypt con factor 10).
-- IMPORTANTE: cambiar esta contrasena inmediatamente despues del primer login.
INSERT INTO usuarios (nombre, email, password_hash, id_rol, activo)
VALUES (
    'Administrador',
    'admin@crediticio.com',
    '$2a$10$dWbPtOZ7a2sjWm7WNiMEte6yddSJyiji5u.15UwPnxeQCW1vhEjYO',
    (SELECT id_rol FROM roles WHERE nombre = 'ADMIN'),
    TRUE
);
