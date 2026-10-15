# Registro de cambios de configuracion y arranque

Bitacora de ajustes necesarios para ejecutar el backend de autenticacion contra la base compartida `db_scoring`. Cada entrada indica sintoma, causa, solucion y como verificar. Todo ajuste de configuracion nuevo debe agregarse aqui.

## 2026-10-14 — HU08: roles, correo, contrasena y primer administrador

Cambios de configuracion/arranque que introduce HU08 (rama `feat/HU08-gestionar-usuarios-roles`).

- **Nuevas variables:** `INIT_ADMIN_EMAIL`, `INIT_ADMIN_PASSWORD`, `INIT_ADMIN_NOMBRE` (primer arranque). Ver README y `.env.example`.
- **Migracion V106:** renombra roles a `ADMINISTRADOR_RIESGO`/`ANALISTA_CREDITO`, normaliza correos, crea el indice unico `uq_usuarios_email_lower`, agrega `creado_por` (tablas `usuarios` y `usuarios_aud`) y **elimina el usuario semilla `admin@crediticio.com` solo si conserva la contrasena publica original**.
- **Efecto en entornos existentes:** tras aplicar V106 puede no quedar ningun administrador. Antes de arrancar con la nueva version definir `INIT_ADMIN_EMAIL` e `INIT_ADMIN_PASSWORD`.
- **Efecto en el ecosistema:** el rol que viaja en el JWT y en `/auth/validate` cambia de `ADMIN`/`ANALISTA` a `ADMINISTRADOR_RIESGO`/`ANALISTA_CREDITO`. Motor de Scoring y frontend deben usar estos nombres al integrarse.
- **Verificacion:** migraciones V100-V106 y flujo (login con otra capitalizacion, creacion, `creado_por`, primer administrador) comprobados en una base PostgreSQL temporal; `mvnw clean test` en JDK 21: 70 pruebas, 0 fallos.

## 2026-10-14 — Primer arranque local en Windows

Entorno: Windows 10, PostgreSQL 16 local con `db_scoring` existente y migraciones V1-V5 del Motor de Scoring ya aplicadas.

### 1. Compilacion fallaba con JDK 24
- **Sintoma:** `cannot find symbol ... getId()/getRol()` en decenas de clases.
- **Causa:** Lombok 1.18.34 (version gestionada por Spring Boot 3.3.5) no soporta JDK 24; no genera getters/setters.
- **Solucion:** usar JDK 21 (el declarado en `pom.xml`). Instalado Temurin 21 y apuntado con `JAVA_HOME` solo para el proceso; JDK 24 sin tocar.
- **Archivos:** ninguno (entorno). Documentado en README (requisitos y problemas comunes).

### 2. `ClassFormatError: Invalid method Code length 0`
- **Sintoma:** al arrancar tras un intento fallido de compilacion.
- **Causa:** clases corruptas en `target/` (compilacion previa fallida / incremental con Lombok).
- **Solucion:** `.\mvnw.cmd clean spring-boot:run`.

### 3. Flyway rechazaba la BD compartida
- **Sintoma:** `Validate failed: Detected applied migration not resolved locally: 1` (hasta la 5).
- **Causa:** `flyway_schema_history` contiene V1-V5 del Motor de Scoring que no existen en este repo; Flyway exige por defecto que toda migracion aplicada este en el classpath. Contradecia el ADR-003.
- **Solucion:** `application.yml` → `spring.flyway.ignore-migration-patterns: "*:missing"`.
- **Archivos:** `src/main/resources/application.yml`, ADR-003 (actualizacion), CLAUDE.md seccion 10.
- **Efecto en la BD:** al arrancar, Flyway aplico V100-V105 en `db_scoring` (tablas `roles`, `usuarios`, `revinfo`, `usuarios_aud`, `roles_aud`, procedimiento `sp_resumen_usuarios_por_rol` y usuario admin semilla).

### 4. Hibernate: tipo de columna no coincide
- **Sintoma:** `Schema-validation: wrong column type encountered in column [id_rol] in table [roles]; found [serial (Types#INTEGER)], but expecting [bigint (Types#BIGINT)]`.
- **Causa:** las entidades usan `Long` (→ BIGINT) pero las migraciones V100/V101 crean `SERIAL` (INTEGER); `ddl-auto: validate` lo detecta. Las pruebas no lo veian porque usan H2 con `create-drop`.
- **Solucion:** `@JdbcTypeCode(SqlTypes.INTEGER)` en el `id` de `RolJpaEntity` y `UsuarioJpaEntity`. No se modifico ninguna migracion ya aplicada ni la API (los ids siguen siendo `Long`).
- **Archivos:** `RolJpaEntity.java`, `UsuarioJpaEntity.java`.

### Verificacion realizada
- `GET /actuator/health` → `UP`.
- `POST /api/v1/auth/login` con el admin semilla → token y rol `ADMIN`.
- `GET /api/v1/usuarios` y `GET /api/v1/auth/validate` con el token → OK.
- `mvnw clean test` en JDK 21 → 58 pruebas, 0 fallos.

### Pendiente / recomendaciones
- **Motor de Scoring:** debe configurar el mismo `ignore-migration-patterns: "*:missing"`, porque ahora la BD tiene V100-V105 que el no conoce.
- Cambiar la contrasena del admin semilla (`admin@crediticio.com`) tras el primer login.
- Considerar una prueba de integracion contra PostgreSQL real (Testcontainers) para detectar divergencias de tipos que H2 oculta (requiere decidir dependencia nueva).
