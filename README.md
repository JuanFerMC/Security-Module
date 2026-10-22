# API de Autenticacion y Gestion de Usuarios

Backend de identidad, roles y tokens del **Sistema de Scoring de Riesgo Crediticio**.

Gestiona el ciclo de vida de usuarios (registro, consulta, activacion/desactivacion, cambio de contrasena), asignacion de roles y autenticacion mediante JWT. Expone endpoints REST consumidos por el frontend y consultados por el Motor de Scoring para validar identidad y permisos.

## Ecosistema

| Componente | Repositorio | Puerto |
|---|---|---|
| Scoring API | `scoring-api` | `8080` |
| **API de Autenticacion** (este repo) | `auth` | `8081` |
| Evaluation Query API | `evaluation-query-api` | `8082` |
| Frontend | `credit-compass` | `3000` |

Auth, Scoring y Query comparten la misma base de datos PostgreSQL (`db_scoring`). Este backend administra las tablas `usuarios`, `roles` y `refresh_tokens`; Scoring API administra solicitantes, reglas y evaluaciones; Query API consulta resultados.

## Tecnologias

- Java 21
- Spring Boot 3.3.5 (Web MVC, Data JPA, Security, Validation, Actuator)
- Maven (con Maven Wrapper)
- PostgreSQL + Flyway (migraciones `V100+`)
- JWT (`jjwt` 0.12.6) — autenticacion stateless
- BCrypt — hashing de contrasenas
- Hibernate Envers — auditoria de cambios en entidades
- springdoc-openapi 2.6.0 (Swagger UI)
- Logstash Logback Encoder 7.4 — logs JSON estructurados
- OWASP Dependency-Check — analisis SCA de vulnerabilidades
- H2 (perfil `test`)
- Lombok
- Docker

## Arquitectura

**Monolito modular** con **arquitectura hexagonal** (Ports & Adapters) interna por modulo.

```
com.crediticio.auth/
├── authentication/          # Login, JWT, refresh, validate
│   ├── application/         # AuthService + DTOs
│   ├── domain/
│   ├── ports/
│   │   ├── input/           # AuthInputPort
│   │   └── output/          # TokenProviderPort, PasswordEncoderPort
│   └── infrastructure/
│       ├── input/           # AuthController
│       └── output/          # JwtTokenProvider, BcryptPasswordEncoderAdapter
├── users/                   # CRUD de usuarios + cambio de contrasena
│   ├── application/         # UserService + DTOs
│   ├── domain/              # Usuario (entidad pura)
│   ├── ports/
│   │   ├── input/           # CreateUser, GetUser, ToggleStatus, ChangePassword
│   │   └── output/          # UserRepositoryPort
│   └── infrastructure/
│       ├── input/           # UsuarioController
│       └── output/          # JPA entity, repository, adapter
├── roles/                   # Catalogo de roles
│   ├── domain/              # Rol (entidad pura)
│   ├── ports/output/        # RolRepositoryPort
│   └── infrastructure/output/
├── config/                  # SecurityConfig, JwtFilter, CORS, OpenAPI
│   └── audit/               # Hibernate Envers (RevisionEntity, RevisionListener)
└── shared/                  # Excepciones, ApiError, GlobalExceptionHandler
```

**Principio clave:** el dominio no importa Spring, JPA ni ningun framework. La aplicacion orquesta a traves de puertos (interfaces). La infraestructura implementa los adaptadores concretos.

## Requisitos previos

- JDK 21 (el proyecto compila con `java.version=21`)
- PostgreSQL con la base de datos `db_scoring` **ya creada** y un usuario con permiso para crear tablas (Flyway crea las tablas, no la base)
- Docker (opcional, para ejecucion en contenedor)

> **Si la aplicacion no arranca**, casi siempre es una de estas tres: faltan las claves RSA (`keys/jwt-privada.pem` y `jwt-publica.pem`), falta `SPRING_DATASOURCE_PASSWORD` o el usuario/base de datos no existen. Ver [Primer arranque](#primer-arranque-paso-a-paso) y [Problemas comunes](#problemas-comunes-al-arrancar).

## Configuracion

### Variables de entorno

| Variable | Descripcion | Default |
|---|---|---|
| `SPRING_DATASOURCE_URL` | URL JDBC | `jdbc:postgresql://localhost:5432/db_scoring` |
| `SPRING_DATASOURCE_USERNAME` | Usuario BD | `postgres` |
| `SPRING_DATASOURCE_PASSWORD` | Contrasena BD | — **(obligatoria)** |
| `JWT_PRIVATE_KEY_LOCATION` | Ruta al archivo PEM de la clave privada RSA (PKCS#8) | `file:./keys/jwt-privada.pem` |
| `JWT_PUBLIC_KEY_LOCATION` | Ruta al archivo PEM de la clave publica RSA | `file:./keys/jwt-publica.pem` |
| `JWT_EXPIRATION_MS` | Vigencia del access token en ms. **Maximo 900000 (15 min)**; la app no arranca si se supera | `900000` (15 min) |
| `JWT_REFRESH_EXPIRATION_DAYS` | Vigencia del refresh token en dias. **Maximo 7**; la app no arranca si se supera | `7` |
| `JWT_ISSUER` | Emisor (`iss`) de los tokens. Scoring API y Query API deben validar el mismo | `crediticio-auth` |
| `JWT_AUDIENCES` | Audiencias (`aud`) de los tokens, separadas por coma. Cada API que valide estos tokens debe estar listada | `scoring-api,evaluation-query-api` |
| `CORS_ALLOWED_ORIGINS` | Origenes permitidos (separados por coma) | `http://localhost:3000` |
| `SERVER_PORT` | Puerto del servidor | `8081` |
| `INIT_ADMIN_EMAIL` | Correo del primer administrador (solo primer arranque) | — |
| `INIT_ADMIN_PASSWORD` | Contrasena del primer administrador, min. 12 caracteres (solo primer arranque) | — |
| `INIT_ADMIN_NOMBRE` | Nombre del primer administrador | `Administrador` |

Hay una plantilla en [`.env.example`](.env.example).

> **Importante:** Spring Boot **no lee el archivo `.env` por si solo**. Las variables deben estar definidas en el entorno del proceso (terminal, IDE, Docker `-e`, servidor). El `.env` esta en `.gitignore` a proposito: los secretos nunca se suben al repositorio, por eso **cada persona y cada entorno debe crear los suyos**.

### Par de claves RSA (RS256)

Los tokens se firman con **RS256** (criptografia asimetrica): la clave privada firma (solo este backend la tiene), la clave publica verifica (se distribuye a Scoring API y Query API).

- Cada entorno (local, pruebas, produccion) tiene **su propio par**. Los archivos PEM nunca se suben al repositorio (estan en `.gitignore`).
- Si se regenera el par, todos los tokens emitidos previamente dejan de ser validos.
- La clave publica debe copiarse a `scoring-api` y `evaluation-query-api` para que validen tokens de forma autonoma.

Generar un par (formato PKCS#8, 2048 bits):

```bash
mkdir -p keys
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out keys/jwt-privada.pem
openssl pkey -in keys/jwt-privada.pem -pubout -out keys/jwt-publica.pem
```

> **Importante:** el archivo de clave privada debe comenzar con `-----BEGIN PRIVATE KEY-----` (PKCS#8), no con `-----BEGIN RSA PRIVATE KEY-----` (PKCS#1). El comando `genpkey` genera PKCS#8 directamente.

### Primer arranque (paso a paso)

1. Tener JDK 21 y PostgreSQL con la base `db_scoring` creada.
2. Clonar el repo:
   ```bash
   git clone https://github.com/Ulisesorozcovi/auth.git
   cd auth
   ```
3. Definir las variables de entorno en la misma terminal donde se va a ejecutar:

   Windows (PowerShell):
   ```powershell
   $env:SPRING_DATASOURCE_USERNAME = "tu_usuario_bd"
   $env:SPRING_DATASOURCE_PASSWORD = "tu_password_bd"
   ```

   Linux/Mac/Git Bash:
   ```bash
   export SPRING_DATASOURCE_USERNAME=tu_usuario_bd
   export SPRING_DATASOURCE_PASSWORD=tu_password_bd
   ```

   Generar las claves RSA (una sola vez por entorno):
   ```bash
   mkdir -p keys
   openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out keys/jwt-privada.pem
   openssl pkey -in keys/jwt-privada.pem -pubout -out keys/jwt-publica.pem
   ```
4. Ejecutar:
   ```bash
   ./mvnw spring-boot:run        # Linux/Mac/Git Bash
   .\mvnw.cmd spring-boot:run    # Windows
   ```
5. Verificar: `http://localhost:8081/actuator/health` debe responder `UP` y `http://localhost:8081/swagger-ui.html` muestra la API.

La aplicacion arranca en `http://localhost:8081`. Flyway ejecuta las migraciones `V100+` automaticamente al iniciar; en una base compartida con el Motor de Scoring conviven con sus migraciones `V1-V99`.

### Despliegue

El repositorio **no contiene** ningun secreto ni la base de datos: al desplegar en cualquier servidor, contenedor o plataforma hay que **configurar ahi mismo** las variables de la tabla anterior (`SPRING_DATASOURCE_*`, `JWT_SECRET`, `CORS_ALLOWED_ORIGINS`). El pipeline de CI (`.github/workflows/ci.yml`) solo compila y prueba contra H2, por eso no necesita secretos; **no despliega**.

Lista de verificacion antes de desplegar:

- [ ] Base `db_scoring` accesible desde el servidor y usuario con permisos de creacion de tablas
- [ ] `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` y `SPRING_DATASOURCE_PASSWORD` definidas
- [ ] Par de claves RSA generado en `keys/` (la clave publica copiada a Scoring API y Query API)
- [ ] `CORS_ALLOWED_ORIGINS` con la URL real del frontend
- [ ] Cambiar la contrasena del admin semilla (ver mas abajo)

### Problemas comunes al arrancar

| Sintoma | Causa | Solucion |
|---|---|---|
| Error de clave RSA / "Key is not in PEM-encoded PKCS#8 format" | Clave privada en formato PKCS#1 o ausente | Regenerar con `openssl genpkey` (ver "Par de claves RSA") |
| `password authentication failed for user ...` | Usuario o contrasena de BD incorrectos, o variable sin definir | Revisar `SPRING_DATASOURCE_USERNAME` y `SPRING_DATASOURCE_PASSWORD` |
| `database "db_scoring" does not exist` | La base no esta creada | Crearla en PostgreSQL |
| `Connection refused` a `localhost:5432` | PostgreSQL apagado o en otro host/puerto | Iniciar el servicio o ajustar `SPRING_DATASOURCE_URL` |
| Las variables "no se toman" | Se definieron en otra terminal, o se uso `.env` esperando que Spring lo lea | Definirlas en la misma terminal/IDE donde se ejecuta |
| "Failed to load ApplicationContext" / error de claves RSA al arrancar | Archivos `keys/jwt-privada.pem` o `jwt-publica.pem` no existen | Generar el par RSA (ver "Par de claves RSA") |
| Error de compilacion de version Java | JDK distinto de 21 | Instalar y usar JDK 21 |
| Puerto 8081 ocupado | Otro proceso lo usa | Cambiar `SERVER_PORT` |
| Peticion con JSON invalido responde 401 en vez de 400 | `/error` protegido por Spring Security (error dispatch del contenedor) | Ya resuelto: `SecurityConfig` permite `/error`. Verificar con servidor real, no solo MockMvc |
| Arranque falla: "JWT_EXPIRATION_MS debe estar entre 1 y 900000" | Se configuro un access token de mas de 15 minutos | Usar 900000 o menos |
| Arranque falla: "JWT_REFRESH_EXPIRATION_DAYS debe estar entre 1 y 7" | Refresh token de mas de 7 dias | Usar 7 o menos |
| Frontend recibe 401 en `/auth/refresh` y no hay otra causa | El refresh token ya se uso (rotado), vencio o la sesion se revoco por reutilizacion | Es esperado: pedir login de nuevo. Guardar siempre el refresh token mas reciente |
| Advertencia "No existe ningun ADMINISTRADOR_RIESGO" / nadie puede crear usuarios | Primer arranque sin `INIT_ADMIN_EMAIL`/`INIT_ADMIN_PASSWORD` (o V106 elimino el seed) | Definir las dos variables y reiniciar; ver "Primer administrador" |
| Arranque falla con "INIT_ADMIN_PASSWORD no cumple la politica" | Contrasena inicial de menos de 12 caracteres | Usar 12 o mas |
| `cannot find symbol ... getId()/getRol()` al compilar | Se compila con JDK 24+: Lombok 1.18.34 (el de Spring Boot 3.3.5) no lo soporta | Usar JDK 21. Con `JAVA_HOME` apuntando a JDK 21 |
| `ClassFormatError: Invalid method Code length 0` al arrancar | Clases en `target/` corruptas por una compilacion previa fallida o incremental con Lombok | `.\mvnw.cmd clean spring-boot:run` |
| `Validate failed: Detected applied migration not resolved locally: 1` | La BD compartida tiene migraciones V1-V99 del Motor de Scoring | Ya resuelto en `application.yml` (`ignore-migration-patterns: "*:missing"`). Si aparece, actualizar la rama |
| `wrong column type encountered in column [id_rol] ... found [serial], expecting [bigint]` | Entidad `Long` vs columna `SERIAL` (INTEGER) | Ya resuelto con `@JdbcTypeCode(SqlTypes.INTEGER)` en `RolJpaEntity` y `UsuarioJpaEntity` |

> Historial detallado de estos ajustes de arranque: [`docs/CAMBIOS_CONFIGURACION.md`](docs/CAMBIOS_CONFIGURACION.md).

### Ejecucion con Docker

```bash
# Construir imagen
docker build -t crediticio-auth .

# Ejecutar contenedor
docker run -p 8081:8081 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/db_scoring \
  -e SPRING_DATASOURCE_USERNAME=postgres \
  -e SPRING_DATASOURCE_PASSWORD=tu_password \
  -v $(pwd)/keys:/app/keys:ro \
  crediticio-auth
```

El Dockerfile usa multi-stage build (JDK 21 para compilar, JRE 21 Alpine para ejecutar), usuario no-root y healthcheck integrado.

### Swagger UI

Disponible en `http://localhost:8081/swagger-ui.html` — incluye esquema Bearer para autenticacion con JWT. Cada endpoint esta documentado con `@Operation`, `@ApiResponse` y `@Schema` en los DTOs.

> **Para el frontend:** la guia de integracion (flujo de sesion, renovacion de tokens, errores, roles y ejemplo de cliente) esta en [`docs/INTEGRACION_FRONTEND.md`](docs/INTEGRACION_FRONTEND.md).

## Endpoints

### Autenticacion (`/api/v1/auth`)

| Metodo | Ruta | Descripcion | Acceso |
|---|---|---|---|
| `POST` | `/login` | Login con email y contrasena → JWT + rol | Publico |
| `POST` | `/refresh` | Canjea un refresh token por un nuevo access token y un nuevo refresh token (rotacion) | Publico (se autentica con el refresh token) |
| `POST` | `/logout` | Invalida la sesion del refresh token (idempotente, 204) | Publico (se autentica con el refresh token) |
| `GET` | `/validate` | Valida token y retorna identidad + rol | ADMINISTRADOR_RIESGO, ANALISTA_CREDITO |

### Usuarios (`/api/v1/usuarios`)

| Metodo | Ruta | Descripcion | Acceso |
|---|---|---|---|
| `POST` | `/` | Crea usuario con rol asignado | ADMINISTRADOR_RIESGO |
| `GET` | `/` | Lista todos los usuarios | ADMINISTRADOR_RIESGO |
| `GET` | `/{id}` | Detalle de un usuario | ADMINISTRADOR_RIESGO (cualquiera), ANALISTA_CREDITO (solo propio) |
| `PATCH` | `/{id}/estado` | Activa o desactiva un usuario | ADMINISTRADOR_RIESGO |
| `PATCH` | `/{id}/password` | Cambia la contrasena de un usuario | ADMINISTRADOR_RIESGO (reset), ANALISTA_CREDITO (solo propia, requiere contrasena actual) |

### Matriz RBAC

| Endpoint | ADMINISTRADOR_RIESGO | ANALISTA_CREDITO | Publico |
|---|---|---|---|
| `POST /auth/login` | — | — | Si |
| `POST /auth/refresh` | — | — | Si (con refresh token) |
| `POST /auth/logout` | — | — | Si (con refresh token) |
| `GET /auth/validate` | Si | Si | — |
| `POST /usuarios` | Si | — | — |
| `GET /usuarios` | Si | — | — |
| `GET /usuarios/{id}` | Si | Solo propio | — |
| `PATCH /usuarios/{id}/estado` | Si | — | — |
| `PATCH /usuarios/{id}/password` | Si (cualquiera) | Solo propia | — |

### Respuesta de error

Todas las respuestas de error siguen el formato `ApiError`:

```json
{
  "errorCode": "VALIDATION_ERROR",
  "message": "Error de validacion en los datos de entrada",
  "details": { "email": "must not be blank" },
  "traceId": "550e8400-e29b-41d4-a716-446655440000",
  "timestamp": "2026-10-06T15:30:00"
}
```

## Modelo de datos

### `roles`

| Columna | Tipo | Restriccion |
|---|---|---|
| `id_rol` | SERIAL | PK |
| `nombre` | VARCHAR(50) | UNIQUE NOT NULL |

Seed: `ADMINISTRADOR_RIESGO`, `ANALISTA_CREDITO`.

### `usuarios`

| Columna | Tipo | Restriccion |
|---|---|---|
| `id_usuario` | SERIAL | PK |
| `uuid_publico` | UUID | UNIQUE NOT NULL, DEFAULT `gen_random_uuid()` |
| `nombre` | VARCHAR(150) | NOT NULL |
| `email` | VARCHAR(255) | UNIQUE NOT NULL |
| `password_hash` | VARCHAR(255) | NOT NULL |
| `id_rol` | INTEGER | FK → `roles.id_rol` |
| `activo` | BOOLEAN | DEFAULT TRUE |
| `fecha_creacion` | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP |
| `fecha_modificacion` | TIMESTAMP | Nullable |

> `uuid_publico` es el identificador que va en el claim `sub` del JWT. El `id_usuario` SERIAL es interno y no se expone en tokens.

### Tablas de auditoria (Hibernate Envers)

| Tabla | Proposito |
|---|---|
| `revinfo` | Registro de cada revision (timestamp + usuario que hizo el cambio) |
| `usuarios_aud` | Historial de cambios en `usuarios` (INSERT/UPDATE/DELETE) |
| `roles_aud` | Historial de cambios en `roles` |

Cada operacion de escritura genera automaticamente una fila en la tabla `_aud` correspondiente con el tipo de operacion (0=INSERT, 1=UPDATE, 2=DELETE) y una copia de todos los campos en ese momento.

### Migraciones Flyway

| Version | Descripcion |
|---|---|
| `V100` | Tabla `roles` + seed ADMIN, ANALISTA |
| `V101` | Tabla `usuarios` con FK a roles |
| `V102` | Indices en `id_rol` (FK) y `activo` |
| `V103` | Tablas de auditoria Envers (`revinfo`, `usuarios_aud`, `roles_aud`) |
| `V104` | Procedimiento almacenado `sp_resumen_usuarios_por_rol()` |
| `V105` | Seed del usuario administrador inicial (retirado por V106) |
| `V106` | HU08: roles `ADMINISTRADOR_RIESGO`/`ANALISTA_CREDITO`, correo normalizado + indice unico `lower(email)`, columna `creado_por`, retiro del seed con credencial publica |
| `V107` | HU09: tabla `refresh_tokens` (hash, familia de sesion, usado/revocado) |
| `V108` | Indice en `refresh_tokens.usuario_id` para lookup por usuario |
| `V109` | Columna `uuid_publico` UUID en `usuarios` (identificador publico para JWT RS256) |

Prefijo `V100+` para no colisionar con las migraciones del Motor de Scoring (`V1-V99`).

Como ambos backends comparten `flyway_schema_history`, este backend tiene configurado `spring.flyway.ignore-migration-patterns: "*:missing"` para no fallar la validacion por las migraciones V1-V99 que aplico el Motor de Scoring y que no existen en este repositorio.

Los ids (`id_rol`, `id_usuario`) son `SERIAL` (INTEGER) en BD y `Long` en Java; las entidades JPA usan `@JdbcTypeCode(SqlTypes.INTEGER)` para que `ddl-auto: validate` los acepte. No cambiar el tipo de la columna sin una nueva migracion.

### Primer administrador (inicializacion controlada)

El endpoint de crear usuarios requiere rol `ADMINISTRADOR_RIESGO`, por lo que el primer administrador se crea **fuera de la API publica**, al arrancar la aplicacion (HU08 RF09/RF10):

1. Definir en el entorno del proceso (nunca en el repositorio):

   | Variable | Descripcion |
   |---|---|
   | `INIT_ADMIN_EMAIL` | Correo del primer administrador (se normaliza a minusculas) |
   | `INIT_ADMIN_PASSWORD` | Contrasena, **minimo 12 caracteres** |
   | `INIT_ADMIN_NOMBRE` | Opcional. Por defecto `Administrador` |

2. Arrancar la aplicacion. Si **no existe ningun** usuario con rol `ADMINISTRADOR_RIESGO`, se crea uno con esos datos (queda `creado_por = BOOTSTRAP`).
3. Cuando ya existe un administrador, la inicializacion **se ignora sola** (queda deshabilitada); conviene retirar las dos variables del entorno.

Notas:
- No existe endpoint publico para esto, y la contrasena nunca se escribe en logs.
- Si no hay administrador y no hay variables, la aplicacion arranca igual pero registra una advertencia: nadie podra crear usuarios hasta inicializarlo.
- La migracion `V106` elimina el antiguo usuario semilla `admin@crediticio.com` **solo si conserva la contrasena publica original**. Si alguien ya cambio su contrasena, no se toca.
- Antes de V106 el README publicaba una contrasena fija; en cualquier entorno donde V105 haya corrido y V106 no, esa credencial es publica y debe considerarse comprometida.

### Politica de usuarios y contrasenas (HU08)

| Regla | Detalle |
|---|---|
| Roles permitidos | Solo `ADMINISTRADOR_RIESGO` y `ANALISTA_CREDITO`; otro rol responde 400 `ROL_NOT_ALLOWED` |
| Correo | Obligatorio, formato valido, se guarda en minusculas; unico sin distinguir mayusculas (indice unico en BD) |
| Contrasena | 12 a 72 caracteres (limite de BCrypt), hash BCrypt |
| Duplicado | 409 `DUPLICATE_EMAIL`, sin crear usuario parcial (tambien ante carreras entre peticiones) |
| Trazabilidad | `fecha_creacion` y `creado_por` (correo del administrador que creo el usuario) |

### Procedimiento almacenado

```sql
SELECT * FROM sp_resumen_usuarios_por_rol();
-- Retorna: rol_nombre | total_usuarios | activos | inactivos
```

Consulta no trivial con `LEFT JOIN`, `GROUP BY` y `FILTER` condicional.

## Flujo de autenticacion

```
 Frontend                         Auth Backend (este repo)           Scoring / Query API
    │  POST /auth/login                  │
    │ ─────────────────────────────────→ │  access token RS256 (15 min) + refresh token (7 dias)
    │ ←───────────────────────────────── │
    │  Authorization: Bearer <access>    │
    │ ─────────────────────────────────→ │ ─────────────────────────────→ valida con clave publica
    │  (access vencido)                  │
    │  POST /auth/refresh {refreshToken} │  invalida el refresh usado y emite uno nuevo
    │ ─────────────────────────────────→ │
    │  POST /auth/logout {refreshToken}  │  invalida la sesion completa
    │ ─────────────────────────────────→ │
```

1. `POST /auth/login` devuelve un **access token** JWT (15 minutos) y un **refresh token** opaco (maximo 7 dias).
2. El frontend envia `Authorization: Bearer <access token>` a ambos backends.
3. Al vencer el access token, `POST /auth/refresh` con el refresh token entrega un par nuevo. **Cada refresh token sirve una sola vez**: el anterior queda invalidado en la misma operacion.
4. Si se presenta un refresh token que ya fue rotado, se asume robo: **toda la sesion (familia) se revoca** y hay que volver a iniciar sesion.
5. `POST /auth/logout` invalida la sesion. El refresh token se guarda solo como hash SHA-256; nunca en claro.
6. Scoring API y Query API validan el access token de forma autonoma con la clave publica RSA (validan `iss`, `aud` y firma RS256), o via `GET /auth/validate`.

Claims del access token: `sub` (UUID publico del usuario), `email`, `role`, `iss` (`crediticio-auth`), `aud` (`scoring-api`, `evaluation-query-api`), `iat`, `exp`, `jti`. El rol efectivo siempre se toma de la BD, no del claim.

## Seguridad

### Medidas implementadas

- **Contrasenas:** hash BCrypt, nunca en texto plano. `password_hash` nunca se expone en respuestas.
- **JWT:** firmado con **RS256** (criptografia asimetrica). La clave privada RSA firma los tokens (solo en este backend); la clave publica se distribuye a Scoring API y Query API para validacion local sin secreto compartido. Access token de 15 minutos con `iss`/`aud`, rechazo de tokens vencidos, alterados o de otro emisor/audiencia.
- **Refresh tokens:** opacos, de un solo uso, guardados solo como hash, con rotacion atomica, deteccion de reutilizacion (revoca la sesion), logout y control de concurrencia.
- **Login:** respuesta unica 401 ante cualquier fallo y comparacion de hash incluso si el usuario no existe (no revela existencia por mensaje ni por tiempo).
- **RBAC:** por endpoint en Spring Security. Politica "solo propio" para ANALISTA_CREDITO en perfil y contrasena.
- **Cambio de contrasena:** ANALISTA_CREDITO debe verificar contrasena actual; ADMINISTRADOR_RIESGO puede hacer reset.
- **Revocacion de acceso:** al desactivar un usuario, cualquier token existente queda inutilizado. El filtro JWT verifica el estado `activo` en BD en cada peticion.
- **CORS:** origenes explicitos configurables, no `*`.
- **Auditoria:** Hibernate Envers registra cada cambio con timestamp y usuario responsable.
- **Logs:** JSON estructurado en produccion (compatible ELK/CloudWatch/Datadog).
- **SCA:** OWASP Dependency-Check falla el build si un CVE tiene CVSS >= 7.

### Analisis OWASP Top 10

Documentado en [`docs/SECURITY_OWASP.md`](docs/SECURITY_OWASP.md). Cubre las 10 categorias con medidas implementadas y recomendaciones pendientes.

### Escaneo de dependencias

```bash
./mvnw dependency-check:check
# Reporte: target/dependency-check-report.html
```

## Pruebas

```bash
./mvnw clean test        # Linux/Mac
.\mvnw.cmd clean test    # Windows
```

| Clase | Tipo | Tests | Cobertura |
|---|---|---|---|
| `UserServiceTest` | Unitario | 18 | createUser (normalizacion, rol permitido, carrera), getAllUsers, getUserById, toggleStatus, changePassword |
| `AuthServiceTest` | Unitario | 19 | login, refresh (rotacion, reutilizacion, vencido, inactivo, carrera), logout, validate |
| `JwtTokenProviderTest` | Unitario | 12 | RS256 claims, iss/aud, vencido, alterado, rechazo HMAC, refresh opaco/hash, limites de configuracion |
| `AdminBootstrapRunnerTest` | Unitario | 4 | Primer administrador por variables de entorno |
| `HttpErrorHandlingIntegrationTest` | Integracion (servidor HTTP real) | 12 | Errores de entrada 400 (JSON mal formado, tipos), UTF-8 en 401/403, `idRol` inexistente, refresh invalido 401 |
| `AuthFlowIntegrationTest` | Integracion | 44 | Login, CRUD, RBAC, cambio contrasena, refresh/logout, concurrencia, usuarios desactivados |
| `AuthApplicationTests` | Contexto | 1 | Spring Boot context loads |

**Total: 110 tests** (verificado con `mvnw clean test` en JDK 21).

## Decisiones arquitectonicas (ADR)

| ADR | Decision |
|---|---|
| [ADR-001](docs/adr/ADR-001-arquitectura-hexagonal.md) | Hexagonal (Ports & Adapters) sobre Package by Layer |
| [ADR-002](docs/adr/ADR-002-autenticacion-jwt-stateless.md) | JWT Stateless sobre sesiones/OAuth2 |
| [ADR-003](docs/adr/ADR-003-base-de-datos-compartida.md) | BD compartida con prefijos Flyway diferenciados |

## CI/CD

GitHub Actions (`.github/workflows/ci.yml`): en cada push/PR a `main` ejecuta tests, construye el JAR y lo sube como artefacto con retencion de 7 dias.

## Coordinacion con Scoring API y Query API

- Los tres backends comparten la misma BD (`db_scoring`) pero **no se llaman entre si directamente** (excepto Scoring → Query con token de servicio).
- Cada backend gestiona sus propias tablas con rangos de migracion Flyway diferenciados.
- El frontend orquesta las peticiones a los tres backends.
- **Distribucion de la clave publica:** la clave `jwt-publica.pem` generada por auth debe copiarse a Scoring API y Query API para que validen tokens RS256 de forma autonoma.
