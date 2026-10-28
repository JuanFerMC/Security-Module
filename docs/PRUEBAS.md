# Pruebas de auth

Desde la carpeta `auth`, ejecutar `mvnw.cmd -B test` (Windows) o `./mvnw -B test` (Linux/macOS).
La suite usa H2 en memoria y un par RSA efímero generado por las pruebas; no requiere Supabase,
credenciales de despliegue ni claves locales. Incluye pruebas unitarias de autenticación, JWT,
usuarios y bootstrap del administrador, además de pruebas de integración HTTP.

Para ejecutar una clase concreta: `mvnw.cmd -Dtest=AuthServiceTest -B test`.
