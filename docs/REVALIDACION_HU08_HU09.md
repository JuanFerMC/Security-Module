# Revalidacion de requisitos HU08 y HU09

Fecha: 2026-10-14. Base revisada: `main` en el commit `965b35e` (HU08 y HU09 ya integradas).

Metodo: lectura del codigo vigente, la suite automatizada (98 pruebas), una sonda temporal de requisitos contra la aplicacion con MockMvc (no versionada) y comprobaciones contra un servidor real (Tomcat embebido + PostgreSQL temporal). La columna **Evidencia** indica con que se comprobo cada punto.

**Leyenda:** ✅ cumple · 🟡 cumple con salvedad · ❌ no cumple

## Resumen

Los requisitos funcionales de ambas HU estan implementados y probados, **pero la revalidacion encontro 4 defectos que las pruebas automaticas no detectaban**, porque MockMvc no reproduce el despacho de errores del contenedor:

| # | Hallazgo | Gravedad | Requisito afectado |
|---|---|---|---|
| H1 | En servidor real, una peticion con **JSON mal formado o tipo invalido responde 401** en lugar de 400 | Alta | HU08 RF13, HU09 RNF06 |
| H2 | Las respuestas 401/403 salen con `charset=ISO-8859-1` y **acentos corruptos** ("Autenticaci�n requerida") | Media | HU08 RF13, HU09 RNF06 |
| H3 | `idRol` inexistente responde **404**; la HU pide 400 para entradas invalidas | Baja (decision) | HU08 RF13 |
| H4 | `refresh`/`logout` con token vacio, ausente o de mas de 512 caracteres responde **400**; la HU pide 401 para tokens invalidos | Baja (decision) | HU09 RF13 |

## HU08 — Gestionar usuarios y roles

| RF/RNF | Estado | Evidencia |
|---|---|---|
| RF01 Registrar usuario | ✅ | Prueba de integracion `createUser_asAdmin_returns201` |
| RF02 Correo obligatorio, valido y normalizado | ✅ | Pruebas de normalizacion y login sin distincion de mayusculas; `@Email` + `EmailNormalizer` |
| RF03 Unicidad (tambien en BD) | ✅ | Indice `uq_usuarios_email_lower` aplicado en PostgreSQL; prueba de duplicado con otra capitalizacion |
| RF04 Contrasena minima 12 | ✅ | 11 caracteres → 400; 73 caracteres → 400 (limite BCrypt); sonda |
| RF05 Hash adaptativo, sin registrar la original | ✅ | BCrypt; no hay `log.` con la contrasena; el hash nunca se devuelve (sonda `GET /usuarios`) |
| RF06 Solo los 2 roles | ✅ | `RolSistema.PERMITIDOS`; prueba `createUser_roleNotAllowed_returns400`; V106 aplicada en PostgreSQL |
| RF07 Solo administrador crea usuarios | ✅ | Analista → 403, sin token → 401 |
| RF08 Sin privilegios por campos manipulados | ✅ | Sonda: cuerpo con `rol`, `rolNombre`, `id` y `activo` manipulados crea un `ANALISTA_CREDITO` activo; campos extra ignorados |
| RF09 Primer administrador fuera de la API | ✅ | `AdminBootstrapRunner`; verificado en PostgreSQL (creado con `creado_por = BOOTSTRAP`) |
| RF10 Inicializacion deshabilitada despues | ✅ | Pruebas unitarias; no existe endpoint publico |
| RF11 Sin usuario parcial | ✅ | Prueba de carrera (`DataIntegrityViolationException` → 409) |
| RF12 Confirmacion sin contrasena ni hash | ✅ | Respuesta con id, nombre, correo, rol, estado y fechas |
| RF13 Codigos 400/401/403 | ❌ | Ver H1, H2 y H3 |
| RNF01 Seguridad | ✅ | Validacion de entradas, lista blanca de roles, BCrypt |
| RNF02 Integridad en persistencia | ✅ | `UNIQUE`, `NOT NULL` y `FOREIGN KEY` en migraciones |
| RNF03 Atomicidad | ✅ | Una transaccion en `UserService` |
| RNF04 Confidencialidad | ✅ | Sin contrasenas ni hashes en respuestas, logs ni excepciones; sin `show-sql` |
| RNF05 Trazabilidad | ✅ | `fecha_creacion`, `creado_por` y log de creacion con el actor |
| RNF06 Configuracion segura | 🟡 | Secretos por entorno. Salvedad: ver H5 |

## HU09 — Autenticar usuario

| RF/RNF | Estado | Evidencia |
|---|---|---|
| RF01 Autenticar | ✅ | Login verificado en PostgreSQL con correo en otra capitalizacion |
| RF02 401 sin revelar la causa | ✅ | Cuerpos de error identicos (prueba y PostgreSQL); contrasenas de 200 y 100000 caracteres → 401 (no 500). Salvedad menor: ver H6 |
| RF03 Access token 15 min | ✅ | `exp - iat = 900` en PostgreSQL |
| RF04 Claims | ✅ | `sub`, `email`, `rol`, `iss`, `aud`, `iat`, `exp`, `jti` leidos del token real |
| RF05 Rechazo de tokens invalidos | ✅ | Vencido, alterado, firma ajena, otro emisor, otra audiencia, `alg=none` y HS512 con la misma clave → 401 |
| RF06 Refresh ≤ 7 dias | ✅ | `expira_en - emitido_en = 7 days` en PostgreSQL |
| RF07 Solo representacion protegida | ✅ | En BD, `token_hash` de 64 caracteres; el valor original no esta |
| RF08 Renovar | ✅ | Verificado en PostgreSQL |
| RF09 Rotacion atomica | ✅ | Token anterior invalidado y nuevo emitido en una transaccion |
| RF10 Reutilizacion revoca la familia | ✅ | Token nuevo de la misma sesion tambien → 401 (PostgreSQL) |
| RF11 Logout | ✅ | 204 y refresh posterior → 401 |
| RF12 Concurrencia | ✅ | 8 renovaciones simultaneas con el mismo token: 1 sola exitosa (H2 y PostgreSQL) |
| RF13 401 por vencido/revocado/invalido | ❌ | Vencido, revocado y desconocido → 401. Salvedad: ver H4 |
| RF14 Sin secretos en respuestas | ✅ | Revisado en todas las respuestas |
| RF15 No confiar en claims | ✅ | Prueba `accessToken_roleClaimIsNotTrusted` |
| RNF01 Criptografia/secretos | ✅ | Falla el arranque con secreto < 32 caracteres |
| RNF02 Vigencias | ✅ | Falla el arranque si se superan 15 min / 7 dias |
| RNF03 Consistencia | ✅ | Revocacion confirmada pese a la excepcion (`noRollbackFor`), vista en PostgreSQL |
| RNF04 Sin tokens ni secretos en logs | ✅ | Revisado el codigo; los logs registran ids y familias |
| RNF05 Eventos de autenticacion | ✅ | Login, fallo, renovacion, reutilizacion, logout |
| RNF06 Errores coherentes | ❌ | Ver H1 y H2 |

## Detalle de hallazgos

### H1 — JSON mal formado responde 401 (Alta)
- **Sintoma (servidor real):** `POST /api/v1/auth/login` con cuerpo `{"email": ` responde `401 UNAUTHORIZED`. En MockMvc responde 400, por eso las pruebas pasan.
- **Causa:** Spring MVC resuelve el error con `sendError(400)`; Tomcat redirige a `/error` y esa ruta cae en `anyRequest().authenticated()`, de modo que el despacho de error termina en el `authenticationEntryPoint`.
- **Alcance:** todo error que pase por `/error` (JSON mal formado, tipo invalido como `idRol: "abc"`, ruta inexistente, parametros faltantes). Comprobado en servidor real solo con JSON mal formado; los demas siguen el mismo mecanismo.
- **Efecto:** el frontend interpretaria un cuerpo invalido como sesion expirada y forzaria cerrar sesion.
- **Correccion propuesta:** permitir `/error` en `SecurityConfig` y agregar un `@ExceptionHandler` para `HttpMessageNotReadableException` y `MethodArgumentTypeMismatchException` con el formato `ApiError` (400). Prueba de integracion con despacho de error real (`@SpringBootTest(webEnvironment = RANDOM_PORT)`).

### H2 — Acentos corruptos en 401/403 (Media)
- **Evidencia:** `Content-Type: application/json;charset=ISO-8859-1` y un byte `F3` suelto en "Autenticaci?n requerida".
- **Causa:** `SecurityConfig` escribe el cuerpo con `response.getWriter()` sin fijar UTF-8.
- **Correccion propuesta:** `response.setCharacterEncoding("UTF-8")` (o `MediaType.APPLICATION_JSON_UTF8`) en ambos manejadores, con prueba que verifique los bytes.

### H3 — `idRol` inexistente responde 404 (decision)
- La HU pide 400 para entradas invalidas; un `idRol` que no existe es un dato de entrada invalido. Hoy responde 404 `ROL_NOT_FOUND`.
- **Opciones:** (a) devolver 400 con `ROL_NOT_FOUND`/`VALIDATION_ERROR` en este endpoint (alineado a la HU); (b) mantener 404 y documentarlo como desviacion. Recomendado (a).

### H4 — Refresh token vacio o muy largo responde 400 (decision)
- La HU pide 401 para tokens "invalidos". Hoy `@NotBlank`/`@Size(max = 512)` devuelven 400 antes de llegar al servicio.
- **Correccion propuesta:** quitar esas validaciones del DTO (el servicio ya trata nulo/vacio como invalido) para que cualquier valor invalido responda 401. Un cuerpo no JSON seguira siendo 400.

### H5 — Credencial publica en el historial (salvedad de RNF06)
- La migracion `V105` conserva en un comentario la contrasena del antiguo admin semilla y su hash, y esa informacion existe en el historial de git.
- V106 elimina el usuario si conserva esa contrasena, y la documentacion actual ya no la publica, pero **no se puede reescribir una migracion aplicada** sin romper su checksum.
- **Accion recomendada:** considerar comprometida esa credencial en cualquier entorno donde V105 haya corrido y V106 no; verificar que no exista el usuario `admin@crediticio.com` con ese hash.

### H6 — Primer intento con usuario inexistente es mas lento (menor)
- El hash de relleno para igualar tiempos se calcula de forma perezosa en la primera peticion con usuario inexistente; esa unica vez toma el doble. Correccion: calcularlo al arrancar.

## Observaciones sin cambio requerido por las HU
- Tras cerrar sesion, el access token ya emitido sigue valido hasta 15 minutos (documentado en `API_REFERENCE.md`).
- Cambiar la contrasena no revoca las sesiones existentes; hoy permanecen hasta 7 dias.
- BCrypt usa el factor por defecto (10). Considerar 12.
- No hay purga de refresh tokens vencidos ni limitacion de intentos de login.

## Plan de correccion propuesto
1. **Rama `fix/HU08-HU09-errores-http-y-codificacion`:** H1, H2, H3 y H4 con pruebas de integracion que usen el servidor real, mas H6.
2. Actualizar `API_REFERENCE.md` (400 para `idRol` inexistente; 401 para refresh invalido) y `VERIFICACION_HU08_HU09.md`.
3. Verificar de nuevo contra Tomcat y PostgreSQL reales antes de cerrar.

Decisiones que requieren confirmacion: H3 (400 vs 404) y H4 (401 para tokens vacios o largos).

---

## Estado tras la correccion (rama `fix/HU08-HU09-errores-http-y-codificacion`)

| Hallazgo | Estado | Verificacion |
|---|---|---|
| H1 JSON mal formado → 401 | ✅ Resuelto | `/error` permitido + manejadores 400. Tomcat real: login y alta con JSON mal formado → 400 `MALFORMED_REQUEST`; `idRol: "abc"` → 400; `/usuarios/abc` → 400 `INVALID_PARAMETER` |
| H2 Acentos corruptos | ✅ Resuelto | Tomcat real: `Content-Type: application/json;charset=UTF-8` y "Autenticación requerida" correcto |
| H3 `idRol` inexistente 404 | ✅ Resuelto | 400 `ROL_NOT_FOUND` (Tomcat real y prueba de integracion) |
| H4 Refresh vacio/largo 400 | ✅ Resuelto | `{}`, `""` y 600 caracteres → 401; logout con 600 caracteres → 204 |
| H5 Credencial en historial de V105 | ⚠️ Sin cambio | No se puede reescribir una migracion aplicada; ver recomendacion arriba |
| H6 Hash de relleno perezoso | ✅ Resuelto | Se calcula al arrancar (`@PostConstruct`) |

Pruebas: 112, sin fallos. Las 12 pruebas de `HttpErrorHandlingIntegrationTest` usan un servidor HTTP real; sin el arreglo, 11 de ellas fallan.

**Hallazgo adicional durante la verificacion:** el algoritmo de firma no es siempre HS256; `jjwt` elige HS256/HS384/HS512 segun la longitud del secreto (con 64 caracteres usa HS512). No afecta a la seguridad ni a las HU, y la documentacion se corrigio.

**Cambios de contrato para el frontend:** `idRol` inexistente ahora es 400 (antes 404) y un refresh token vacio o muy largo ahora es 401 (antes 400).
