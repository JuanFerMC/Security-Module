# ADR-002: Autenticación JWT Stateless

**Estado:** Aceptado  
**Fecha:** 2026-10-06  
**Autores:** Equipo de desarrollo

## Contexto

El sistema tiene dos backends independientes (Auth y Motor de Scoring) y un frontend React. Se necesita un mecanismo de autenticación que permita:

- Que el frontend autentique al usuario una sola vez y luego envíe peticiones a ambos backends.
- Que el Motor de Scoring pueda verificar la identidad del usuario sin llamar al backend de Auth en cada request.
- Escalar horizontalmente sin compartir estado de sesión entre instancias.

Se evaluaron tres alternativas:

1. **Sesiones del lado del servidor** (cookie + session store): requiere un store compartido (Redis) entre los dos backends, y no escala bien horizontalmente sin infraestructura adicional.
2. **OAuth 2.0 con servidor de autorización dedicado** (Keycloak, Auth0): robusto pero introduce un componente adicional y complejidad operativa desproporcionada para el alcance actual (2 roles, CRUD de usuarios).
3. **JWT stateless**: el token contiene la identidad y el rol, firmado con una clave compartida. Cada backend valida el token de forma autónoma sin estado del servidor.

## Decisión

Adoptamos **JWT stateless** con la librería `jjwt` (io.jsonwebtoken).

- El token contiene: `sub` (email), `rol`, `iat`, `exp`.
- La clave secreta (`JWT_SECRET`) se comparte entre Auth y Motor de Scoring vía variable de entorno.
- Expiración configurable (`JWT_EXPIRATION_MS`, default y máximo 15 minutos desde HU09; ver ADR-005).
- El token viaja en el header `Authorization: Bearer <token>`.
- La emisión y validación están aisladas detrás de `TokenProviderPort`, permitiendo cambiar la implementación sin afectar el dominio.

## Consecuencias

### Positivas

- Ambos backends validan el token de forma autónoma — sin llamadas entre servicios ni estado compartido.
- Escala horizontalmente sin infraestructura adicional (no hay session store).
- El frontend gestiona un solo token para ambos backends.
- `TokenProviderPort` permite migrar a otra librería JWT o a un esquema de tokens diferente sin cambiar la lógica de negocio.

### Negativas

- Un token emitido no se puede revocar antes de su expiración (salvo implementar una blacklist, que reintroduce estado).
- Si se compromete `JWT_SECRET`, todos los tokens son vulnerables hasta rotar la clave.
- La información del token (email, rol) puede quedar desactualizada si el usuario cambia de rol o se desactiva antes de que el token expire. El endpoint `GET /auth/validate` mitiga esto consultando el estado actual del usuario.
