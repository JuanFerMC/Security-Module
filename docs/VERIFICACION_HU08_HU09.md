# Verificacion de HU08 y HU09 contra el estado del repositorio

Fecha de la revision: 2026-10-14. Alcance: solo lectura de codigo y documentacion; no se ejecuto ni modifico codigo.

**Leyenda:** ✅ cumple · 🟡 parcial · ❌ no existe

## Resumen

El servicio cubre login basico y creacion de usuarios, pero faltan piezas que las HU exigen de forma explicita: refresh tokens con rotacion y revocacion, claims `iss`/`aud`/id de usuario, vigencia de 15 minutos, contrasena de 12 caracteres, normalizacion de correo, nombres de rol de la HU y un procedimiento controlado para el primer administrador.

## HU08 — Gestionar usuarios y roles

| RF/RNF | Estado | Hallazgo |
|---|---|---|
| RF01 Registrar usuario | ✅ | `POST /api/v1/usuarios` recibe correo, contrasena, rol (y `nombre`). |
| RF02 Validar y normalizar correo | 🟡 | Valida `@NotBlank` y `@Email`; **no normaliza** (sin `trim`/`toLowerCase`). |
| RF03 Unicidad | 🟡 | `UNIQUE` en BD, pero distingue mayusculas: `A@x.com` y `a@x.com` conviven. |
| RF04 Contrasena >= 12 | ❌ | `CreateUserRequest` usa `@Size(min = 8)`. |
| RF05 Hash adaptativo | ✅ | BCrypt (factor 10 por defecto). |
| RF06 Solo 2 roles permitidos | ❌ | Roles actuales `ADMIN` y `ANALISTA`; la HU pide `ADMINISTRADOR_RIESGO` y `ANALISTA_CREDITO`. Se asigna por `idRol` numerico: cualquier fila nueva en `roles` seria asignable. |
| RF07 Solo admin crea usuarios | ✅ | `hasRole("ADMIN")` en `SecurityConfig`. |
| RF08 Sin escalada de privilegios | 🟡 | Un analista recibe 403 al crear. El rol viene del body (`idRol`) sin lista blanca explicita. |
| RF09 Primer admin por procedimiento operativo | ❌ | Es un seed en la migracion V105 con correo y contrasena fijos y publicos (documentados en README). |
| RF10 Deshabilitar inicializacion | ❌ | No existe mecanismo; el seed corre siempre. |
| RF11 Sin usuario parcial ante duplicado | ✅ | `@Transactional` + verificacion previa. Falta cubrir la carrera (dos peticiones simultaneas) capturando la violacion de unicidad. |
| RF12 Confirmacion sin hash | ✅ | `UserResponse` no expone contrasena ni hash. |
| RF13 Codigos 400/401/403 | ✅ | Existen. El duplicado responde 409 (la HU no lo define). |
| RNF03 Atomicidad | ✅ | Una transaccion en `UserService`. |
| RNF04 Confidencialidad en logs/errores | 🟡 | No hay `log.` en el codigo; por verificar que Hibernate/logback no impriman parametros. |
| RNF05 Trazabilidad | 🟡 | `fecha_creacion` y `username` de Envers por revision. No hay `creado_por` ni evento de auditoria explicito. |
| RNF06 Secretos externos | 🟡 | `JWT_SECRET` y contrasena de BD vienen del entorno. Sigue la contrasena del admin semilla en V105. |

## HU09 — Autenticar usuario

| RF/RNF | Estado | Hallazgo |
|---|---|---|
| RF01 Autenticar credenciales | ✅ | `POST /api/v1/auth/login`. No normaliza el correo. |
| RF02 401 sin revelar que fallo | 🟡 | Siempre 401, pero el usuario desactivado responde "Usuario desactivado". Ademas "usuario no existe" responde mas rapido que "contrasena incorrecta" (no hay BCrypt): enumeracion por tiempo. |
| RF03 Access token 15 min | 🟡 | Token unico con `JWT_EXPIRATION_MS=3600000` (1 hora). |
| RF04 Claims id, rol, iat, exp, iss, aud | ❌ | Solo `sub` = correo, `rol`, `iat`, `exp`. Faltan id de usuario, `iss`, `aud`. |
| RF05 Validar iss/aud/firma/exp | 🟡 | Valida firma y expiracion; no emisor ni audiencia. |
| RF06 Refresh token (<= 7 dias) | ❌ | No existe. `POST /refresh` recibe el access token y emite otro; vencido no se puede renovar. |
| RF07 Refresh protegido (hash en BD) | ❌ | Sin tabla ni almacenamiento. |
| RF08 Renovar con refresh valido | ❌ | Ver RF06. |
| RF09 Rotacion atomica | ❌ | No existe. |
| RF10 Deteccion de reutilizacion / familia | ❌ | No existe. |
| RF11 Cerrar sesion | ❌ | Sin endpoint de logout. |
| RF12 Concurrencia en renovacion | ❌ | No existe. |
| RF13 401 por refresh vencido/revocado | ❌ | Depende de RF06–RF12. |
| RF14 No exponer secretos | ✅ | Ninguna respuesta incluye hash ni clave. |
| RF15 No confiar en roles externos | ✅ | El filtro toma el rol de la BD segun el correo del token. |
| RNF01 Firma y secretos seguros | 🟡 | HS256 con secreto externo; la longitud minima no se valida al arrancar. |
| RNF02 Vigencia 15 min / 7 dias | ❌ | Ver RF03 y RF06. |
| RNF03 Consistencia atomica | ❌ | Sin persistencia de sesion. |
| RNF05 Eventos de autenticacion | ❌ | No hay logs de login, fallo ni renovacion. |
| RNF06 Errores coherentes | 🟡 | Mensajes distintos entre fallos de login (ver RF02). |

## Estado de la documentacion

- No existe trazabilidad HU → codigo → prueba.
- `docs/API_REFERENCE.md` y `README.md` describen el comportamiento actual, que difiere de las HU (roles `ADMIN`/`ANALISTA`, token de 1 h, refresh con el mismo token, contrasena minima de 8, credencial del admin en claro). Deben actualizarse junto con el codigo.
- Faltan: procedimiento operativo del primer administrador, modelo de sesiones y refresh tokens, politica de contrasenas, catalogo de eventos de auditoria.
- Pruebas (58): sin cobertura de normalizacion de correo, 12 caracteres, `iss`/`aud`, refresh, rotacion ni concurrencia. Cobertura por prueba no verificada individualmente.

## Decisiones pendientes

1. **Nombres de rol:** ¿adoptar `ADMINISTRADOR_RIESGO` / `ANALISTA_CREDITO` (HU) en lugar de `ADMIN` / `ANALISTA`? Ver seccion siguiente.
2. **Duplicado:** ¿responder 409 (actual) o 400 (unico codigo de entrada invalida que enumera la HU)?
3. **Primer administrador:** ¿variables de entorno de inicializacion leidas una vez al arrancar, o un comando operativo aparte?

### Uso de los nombres de rol en el ecosistema (verificado 2026-10-14)

| Componente | Estado |
|---|---|
| Auth (este repo) | Usa `ADMIN` / `ANALISTA` (V100, `SecurityConfig`, DTOs, README). |
| Motor de Scoring (`motor-scoring-crediticio`) | `SecurityConfig` hace `anyRequest().permitAll()`; no valida ni usa roles. Su `claude-progress.md` deja autenticacion y roles como dependencia futura. |
| Frontend (`credit-compass`) | Sin nombres de rol en `src/`. `AGENTS.md` planea integrar con "ADMIN/ANALISTA" (pendiente). |
| HU08 | `ADMINISTRADOR_RIESGO` y `ANALISTA_CREDITO`. |

Conclusion: ningun otro componente depende hoy de los nombres, por lo que adoptar los de la HU cuesta poco ahora y mas despues. Tambien exige actualizar el plan del frontend.

## Plan propuesto (sin ejecutar)

1. Resolver las decisiones pendientes.
2. Migraciones V106+: `UNIQUE` sobre `lower(email)` y normalizacion; renombrado de roles (si se aprueba); tabla `refresh_tokens` (`id`, `usuario_id`, `familia_id`, `token_hash`, `emitido_en`, `expira_en`, `usado_en`, `revocado_en`); `creado_por` en `usuarios`; retirar el seed fijo V105 del flujo normal.
3. HU08: normalizar correo, contrasena minima 12, lista blanca de roles, inicializacion del primer admin con bloqueo posterior, registrar al actor.
4. HU09: access token de 15 min con `iss`, `aud` e id de usuario; refresh opaco guardado solo como hash; rotacion atomica con bloqueo; deteccion de reutilizacion; logout; respuesta unica de 401 y comparacion de tiempo constante.
5. Auditoria y logs: eventos de login, fallo, refresh y logout sin tokens ni credenciales.
6. Pruebas: una por cada RF/RNF marcado ❌ o 🟡, incluida la concurrencia de refresh.
7. Documentacion en el mismo cambio: matriz de trazabilidad, `API_REFERENCE.md`, ADR de sesiones y refresh tokens, procedimiento del primer admin y bitacora `CAMBIOS_CONFIGURACION.md`.

**Impacto externo:** cambiar duracion del token, roles o flujo de refresh afecta al frontend y al Motor de Scoring cuando integren autenticacion.

---

## Actualizacion: HU08 implementada (rama `feat/HU08-gestionar-usuarios-roles`)

Decisiones tomadas: roles de la HU (`ADMINISTRADOR_RIESGO`, `ANALISTA_CREDITO`) y 409 ante correo duplicado. Primer administrador por variables de entorno (ver `docs/adr/ADR-004-hu08-roles-correo-primer-administrador.md`).

| RF/RNF | Estado | Como se resolvio |
|---|---|---|
| RF02 Normalizar correo | ✅ | `EmailNormalizer` (minusculas, sin espacios laterales) en alta y login; V106 normaliza los existentes |
| RF03 Unicidad | ✅ | Indice unico `lower(email)` (V106) + captura de la violacion en carreras |
| RF04 Contrasena >= 12 | ✅ | `@Size(min = 12, max = 72)` en alta y cambio de contrasena |
| RF06 Roles de la HU | ✅ | V106 + `RolSistema`; el servicio rechaza otros roles con 400 `ROL_NOT_ALLOWED` |
| RF08 Sin escalada | ✅ | Lista blanca de roles en servicio; endpoint solo `ADMINISTRADOR_RIESGO` |
| RF09 Primer admin controlado | ✅ | `AdminBootstrapRunner` con `INIT_ADMIN_*`, fuera de la API |
| RF10 Inicializacion deshabilitada | ✅ | Se omite si ya existe un `ADMINISTRADOR_RIESGO`; seed V105 retirado por V106 |
| RF11 Sin usuario parcial | ✅ | Verificacion previa + `DataIntegrityViolationException` -> 409 |
| RF13 Codigos | ✅ | 400 / 401 / 403 (+ 409 duplicado, decision tomada) |
| RNF04 Confidencialidad | ✅ | Los nuevos logs no incluyen contrasenas ni hashes |
| RNF05 Trazabilidad | ✅ | `creado_por` + log de creacion con actor |
| RNF06 Secretos externos | ✅ | Sin credenciales en migraciones nuevas ni en documentacion |

**Pendiente de HU08:** nada funcional. Queda decidir si se documenta en un contrato versionado para Motor de Scoring/frontend el cambio de nombres de rol.
**Sin tocar (HU09):** todo lo marcado ❌/🟡 de la seccion HU09 sigue pendiente.
