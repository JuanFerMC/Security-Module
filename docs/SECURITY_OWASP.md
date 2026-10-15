# Analisis de Seguridad — OWASP Top 10 (2021)

**Proyecto:** API de Autenticacion y Gestion de Usuarios  
**Fecha:** 2026-10-07  
**Autores:** Equipo de desarrollo

Este documento analiza como el backend aborda cada categoria del OWASP Top 10 (2021), las medidas implementadas y las mitigaciones pendientes.

---

## A01:2021 — Broken Access Control

**Riesgo:** Un usuario accede a recursos o funciones que no le corresponden.

**Medidas implementadas:**
- RBAC por endpoint en `SecurityConfig.java` con Spring Security (`hasRole("ADMINISTRADOR_RIESGO")`, `hasAnyRole("ADMINISTRADOR_RIESGO", "ANALISTA_CREDITO")`).
- Regla "solo propio" en `GET /usuarios/{id}`: el ANALISTA_CREDITO solo puede consultar su propio perfil; el controlador verifica la identidad del token vs el recurso solicitado.
- Autenticacion stateless: cada request se valida de forma independiente mediante el JWT en el header `Authorization`.
- Respuestas 401 (no autenticado) y 403 (sin permisos) con cuerpo JSON estandarizado (`ApiError`).

**Mitigaciones adicionales recomendadas:**
- Agregar rate limiting por IP o por usuario para prevenir enumeracion de IDs.
- Registrar intentos de acceso denegado en logs de auditoria.

---

## A02:2021 — Cryptographic Failures

**Riesgo:** Datos sensibles expuestos por uso inadecuado de criptografia.

**Medidas implementadas:**
- Contrasenas almacenadas exclusivamente como hash BCrypt (`BcryptPasswordEncoderAdapter`), nunca en texto plano.
- BCrypt aislado detras de `PasswordEncoderPort`, permitiendo migrar a Argon2 sin cambiar la logica de negocio.
- JWT firmado con HMAC-SHA256 (`jjwt`). Clave secreta inyectada via variable de entorno `JWT_SECRET`, nunca hardcodeada.
- `password_hash` nunca se expone en DTOs de salida (`UserResponse` no incluye ese campo).

**Mitigaciones adicionales recomendadas:**
- Usar `JWT_SECRET` de al menos 256 bits en produccion.
- Considerar rotacion periodica de `JWT_SECRET` con soporte para multiples claves.

---

## A03:2021 — Injection

**Riesgo:** Inyeccion de SQL, HQL, LDAP u otros interpretes.

**Medidas implementadas:**
- Persistencia via Spring Data JPA con queries parametrizadas — no hay concatenacion de SQL.
- Procedimiento almacenado (`sp_resumen_usuarios_por_rol`) con parametros tipados en PL/pgSQL.
- Bean Validation (`@NotBlank`, `@Email`, `@Size`) en todos los DTOs de entrada.
- `GlobalExceptionHandler` captura `MethodArgumentNotValidException` y retorna errores de validacion sin exponer internos.

**Mitigaciones adicionales recomendadas:**
- Si se agregan queries nativas futuras, usar siempre parametros nombrados (`:param`), nunca concatenacion.

---

## A04:2021 — Insecure Design

**Riesgo:** Falta de controles de seguridad por diseno deficiente.

**Medidas implementadas:**
- Arquitectura hexagonal: la logica de seguridad (hashing, tokens) esta aislada detras de puertos, permitiendo cambiar implementaciones sin afectar el dominio.
- Separacion de responsabilidades: controladores delegan a servicios de aplicacion, que delegan a puertos.
- ADRs documentan las decisiones de seguridad y sus trade-offs (ADR-001, ADR-002, ADR-003).
- Tests unitarios y de integracion validan la matriz RBAC completa.

---

## A05:2021 — Security Misconfiguration

**Riesgo:** Configuraciones por defecto inseguras o excesivas.

**Medidas implementadas:**
- CSRF deshabilitado (apropiado para API REST stateless con JWT).
- CORS configurado con origenes explicitos via `CORS_ALLOWED_ORIGINS`, no `*`.
- Spring Security exige autenticacion en todos los endpoints excepto los explicitamente publicos.
- `open-in-view: false` deshabilitado para evitar lazy loading fuera de transaccion.
- `ddl-auto: validate` en produccion — el esquema solo cambia via Flyway.
- Actuator expone solo `/health` como publico.
- Swagger UI accesible sin autenticacion (solo en desarrollo; en produccion se recomienda restringir).

**Mitigaciones adicionales recomendadas:**
- Deshabilitar Swagger UI en perfil `prod` (`springdoc.swagger-ui.enabled=false`).
- Configurar headers de seguridad HTTP (X-Content-Type-Options, X-Frame-Options, Strict-Transport-Security).

---

## A06:2021 — Vulnerable and Outdated Components

**Riesgo:** Dependencias con vulnerabilidades conocidas.

**Medidas implementadas:**
- Plugin OWASP Dependency-Check configurado en `pom.xml`. Ejecutar con:
  ```bash
  ./mvnw dependency-check:check
  ```
  Genera reporte HTML/JSON en `target/`. Falla el build si un CVE tiene CVSS >= 7.
- Dependencias gestionadas por Spring Boot BOM (`spring-boot-starter-parent 3.3.5`), que parcha versiones centralizadamente.
- CI pipeline ejecuta tests en cada push/PR.

**Mitigaciones adicionales recomendadas:**
- Integrar `dependency-check:check` en el pipeline de CI como paso obligatorio.
- Configurar Dependabot o Renovate en GitHub para actualizacion automatica de dependencias.

---

## A07:2021 — Identification and Authentication Failures

**Riesgo:** Autenticacion debil o bypass de autenticacion.

**Medidas implementadas:**
- BCrypt con factor de costo por defecto (10) para hashing de contrasenas.
- Validacion de contrasena de 12 a 72 caracteres (`@Size(min = 12, max = 72)`; 72 es el limite de BCrypt).
- Correo normalizado y unicidad por indice `lower(email)`; sin credenciales sembradas en migraciones (primer administrador por variables de entorno).
- Token JWT con expiracion configurable (default 1 hora).
- Usuarios inactivos (`activo = false`) no pueden autenticarse — `AuthService.login()` verifica el estado.
- Endpoint `/validate` consulta el estado actual del usuario en BD, no solo el token.

**Mitigaciones adicionales recomendadas:**
- Implementar bloqueo de cuenta tras N intentos fallidos.
- Agregar requisitos de complejidad de contrasena (mayusculas, numeros, caracteres especiales).
- Considerar refresh token con rotacion (el refresh actual reemite el mismo token sin rotacion).

---

## A08:2021 — Software and Data Integrity Failures

**Riesgo:** Codigo o datos modificados sin verificacion de integridad.

**Medidas implementadas:**
- Migraciones Flyway versionadas e inmutables — no se modifican migraciones ya aplicadas.
- CI pipeline valida tests en cada push/PR antes de merge a `main`.
- Auditoria completa con Hibernate Envers: cada INSERT, UPDATE y DELETE queda registrado en tablas `_aud` con timestamp y usuario responsable.

**Mitigaciones adicionales recomendadas:**
- Firmar los artefactos JAR en el pipeline de CI.
- Agregar verificacion de checksums de dependencias Maven.

---

## A09:2021 — Security Logging and Monitoring Failures

**Riesgo:** Eventos de seguridad no registrados o no monitoreados.

**Medidas implementadas:**
- Logs estructurados en JSON para produccion (`logback-spring.xml` con LogstashEncoder), compatibles con ELK Stack, CloudWatch, Datadog.
- Campo `service: crediticio-auth` incluido automaticamente en cada linea de log.
- `traceId` (UUID) en cada respuesta de error (`ApiError`) para correlacion.
- Auditoria Envers registra quien modifico cada entidad y cuando.

**Mitigaciones adicionales recomendadas:**
- Agregar logging explicito de eventos de seguridad: login exitoso, login fallido, cambio de estado de usuario, token invalido.
- Configurar alertas sobre patrones anomalos (multiples logins fallidos, accesos denegados repetidos).

---

## A10:2021 — Server-Side Request Forgery (SSRF)

**Riesgo:** El servidor realiza peticiones HTTP a destinos controlados por el atacante.

**Medidas implementadas:**
- Este backend no realiza peticiones HTTP salientes a URLs proporcionadas por el usuario.
- No hay funcionalidad de webhook, callback, proxy o import de URLs externas.

**Estado:** No aplica en la arquitectura actual. Monitorear si se agregan integraciones externas.

---

## Comando para ejecutar el analisis de dependencias

```bash
# Escanear dependencias contra la base de datos NVD de vulnerabilidades
./mvnw dependency-check:check

# El reporte se genera en:
# target/dependency-check-report.html
# target/dependency-check-report.json

# Falla el build si algun CVE tiene CVSS >= 7.0
```

## Resumen

| Categoria OWASP | Estado | Nivel |
|---|---|---|
| A01 Broken Access Control | Implementado | Alto |
| A02 Cryptographic Failures | Implementado | Alto |
| A03 Injection | Implementado | Alto |
| A04 Insecure Design | Implementado | Alto |
| A05 Security Misconfiguration | Implementado | Medio |
| A06 Vulnerable Components | Implementado (plugin) | Medio |
| A07 Auth Failures | Implementado | Medio |
| A08 Data Integrity | Implementado | Alto |
| A09 Logging/Monitoring | Implementado | Medio |
| A10 SSRF | No aplica | — |
