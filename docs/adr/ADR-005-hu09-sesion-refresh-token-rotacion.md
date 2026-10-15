# ADR-005: Sesion con access token corto y refresh token rotatorio

## Estado

Aceptado — 2026-10-14. Complementa a ADR-002 (JWT stateless).

## Contexto

HU09 exige access token de 15 minutos con emisor y audiencia, refresh token de hasta 7 dias almacenado solo como hash, rotacion en cada renovacion, deteccion de reutilizacion, cierre de sesion y control de concurrencia. El diseno previo emitia un unico JWT de 1 hora que se "renovaba" presentando el propio JWT, sin posibilidad de revocar ni de renovar tras vencer.

## Decision

1. **Access token:** JWT firmado con HMAC (HS256/HS384/HS512 segun la longitud del secreto; con un secreto de 64 caracteres jjwt usa HS512) de 15 minutos. `sub` = id de usuario; claims `email`, `rol`, `iss`, `aud`, `iat`, `exp`, `jti`. El filtro valida firma, vigencia, emisor y audiencia, y **toma identidad y rol de la BD** (no del claim).
2. **Refresh token:** 32 bytes aleatorios (`SecureRandom`) en Base64URL, opaco. Se persiste solo `SHA-256(token)` en `refresh_tokens`. El hash rapido es adecuado porque el token tiene 256 bits de entropia (no es una contrasena elegida por una persona).
3. **Familias:** cada login crea una familia (`familia_id`); cada rotacion emite un token nuevo en la misma familia.
4. **Rotacion y concurrencia:** el canje es un `UPDATE ... SET usado_en = ahora WHERE id = ? AND usado_en IS NULL AND revocado_en IS NULL`; la BD serializa la fila y solo una peticion actualiza 1 fila. La perdedora se trata como reutilizacion.
5. **Reutilizacion:** un token con `usado_en` o `revocado_en` presentado de nuevo revoca toda la familia y responde 401. La revocacion se confirma aunque se lance la excepcion (`noRollbackFor`).
6. **Logout:** revoca la familia del token presentado; idempotente (204).
7. **Endpoints publicos:** `refresh` y `logout` no exigen access token (puede haber vencido); su proteccion es el refresh token.
8. **Fallo de arranque:** la aplicacion no inicia con `JWT_SECRET` < 32 caracteres, access token > 15 minutos, refresh > 7 dias, o emisor/audiencia vacios.
9. **Login:** respuesta unica `Credenciales inválidas`; siempre se evalua un hash BCrypt (real o de relleno) para igualar tiempos.

## Alternativas consideradas

- **Lista negra de access tokens:** reintroduce estado en cada peticion. Se descarta: con 15 minutos de vigencia y verificacion de usuario activo en BD, el riesgo residual es acotado.
- **Bloqueo pesimista (`SELECT ... FOR UPDATE`):** equivalente en seguridad; el `UPDATE` condicional es mas simple y no mantiene el bloqueo durante la logica de negocio.
- **Expiracion absoluta de la familia:** limitaria la sesion a 7 dias totales. Se decidio vigencia por token (cada rotacion otorga hasta 7 dias), tal como lo expresa la HU; puede endurecerse despues.

## Consecuencias

- **Cambio de contrato:** `POST /auth/refresh` ya no usa el encabezado `Authorization`; recibe `{refreshToken}` y devuelve dos tokens. El frontend debe guardar y reemplazar ambos y no disparar renovaciones paralelas con el mismo token.
- El rol del JWT sigue usando `ADMINISTRADOR_RIESGO`/`ANALISTA_CREDITO` (ADR-004). El `sub` ya no es el correo: el Motor de Scoring que decodifique el token debe leer `email` o usar `GET /auth/validate`.
- El Motor de Scoring, si valida de forma autonoma, debe verificar `iss` y `aud` con los mismos valores (`JWT_ISSUER`, `JWT_AUDIENCE`).
- Los access tokens ya emitidos de la version anterior (sin `iss`/`aud`) dejan de ser validos al desplegar.
- Un refresh token ausente, vacio o de longitud imposible responde 401 (no 400), como exige HU09 RF13. Solo un cuerpo que no es JSON valido responde 400.
- Pendiente: purga de tokens vencidos, limitacion de tasa en login/renovacion y revocar sesiones al cambiar la contrasena.
