# API de Autenticacion y Gestion de Usuarios

Backend de identidad, roles y tokens del **Sistema de Scoring de Riesgo Crediticio**.

Gestiona el ciclo de vida de usuarios (registro, consulta, activacion/desactivacion, cambio de contrasena), asignacion de roles y autenticacion mediante JWT. Expone endpoints REST consumidos por el frontend y consultados por el Motor de Scoring para validar identidad y permisos.

## Ecosistema

| Componente | Repositorio | Puerto |
|---|---|---|
| Motor de Scoring | `motor-scoring-crediticio` | `8080` |
| **API de Autenticacion** (este repo) | `crediticio.auth` | `8081` |
| Frontend | `credit-compass` | `3000` |

Los tres comparten la misma base de datos PostgreSQL (`db_scoring`). Este backend administra las tablas `usuarios` y `roles`; el Motor de Scoring administra solicitantes, reglas y evaluaciones.

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

> **Si la aplicacion no arranca**, casi siempre es una de estas tres: falta `JWT_SECRET`, falta `SPRING_DATASOURCE_PASSWORD` o el usuario/base de datos no existen. Ver [Primer arranque](#primer-arranque-paso-a-paso) y [Problemas comunes](#problemas-comunes-al-arrancar).

## Configuracion

### Variables de entorno

| Variable | Descripcion | Default |
|---|---|---|
| `SPRING_DATASOURCE_URL` | URL JDBC | `jdbc:postgresql://localhost:5432/db_scoring` |
| `SPRING_DATASOURCE_USERNAME` | Usuario BD | `postgres` |
| `SPRING_DATASOURCE_PASSWORD` | Contrasena BD | — **(obligatoria)** |
| `JWT_SECRET` | Clave para firmar tokens (min. 256 bits = 32+ caracteres) | — **(obligatoria)** |
| `JWT_EXPIRATION_MS` | Expiracion del token en ms | `3600000` (1 hora) |
| `CORS_ALLOWED_ORIGINS` | Origenes permitidos (separados por coma) | `http://localhost:3000` |
| `SERVER_PORT` | Puerto del servidor | `8081` |

Hay una plantilla en [`.env.example`](.env.example).

> **Importante:** Spring Boot **no lee el archivo `.env` por si solo**. Las variables deben estar definidas en el entorno del proceso (terminal, IDE, Docker `-e`, servidor). El `.env` esta en `.gitignore` a proposito: los secretos nunca se suben al repositorio, por eso **cada persona y cada entorno debe crear los suyos**.

### De donde sale `JWT_SECRET`

No viene de ningun servicio: **lo inventa quien despliega, una sola vez**. Es la clave con la que el backend firma los tokens de login.

- Debe ser aleatoria y de 32 caracteres o mas.
- Si cambia, todos los tokens emitidos dejan de ser validos (los usuarios deben volver a iniciar sesion).
- **Debe ser la misma** en este backend y en el Motor de Scoring si este valida tokens de forma autonoma.
- Cada entorno (local, pruebas, produccion) usa una distinta. Nunca se escribe en el codigo ni se sube a git.

Generar una:

```bash
openssl rand -base64 48                # Linux/Mac/Git Bash
```

```powershell
[Convert]::ToBase64String((1..48 | ForEach-Object { Get-Random -Maximum 256 }))   # Windows PowerShell
```

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
   $env:JWT_SECRET = "<valor generado arriba>"
   ```

   Linux/Mac/Git Bash:
   ```bash
   export SPRING_DATASOURCE_USERNAME=tu_usuario_bd
   export SPRING_DATASOURCE_PASSWORD=tu_password_bd
   export JWT_SECRET="<valor generado arriba>"
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
- [ ] `JWT_SECRET` propio del entorno (misma que el Motor de Scoring)
- [ ] `CORS_ALLOWED_ORIGINS` con la URL real del frontend
- [ ] Cambiar la contrasena del admin semilla (ver mas abajo)

### Problemas comunes al arrancar

| Sintoma | Causa | Solucion |
|---|---|---|
| Error de clave JWT / `WeakKeyException` / "key byte array is X bits" | `JWT_SECRET` vacia o menor a 32 caracteres | Definirla con 32+ caracteres |
| `password authentication failed for user ...` | Usuario o contrasena de BD incorrectos, o variable sin definir | Revisar `SPRING_DATASOURCE_USERNAME` y `SPRING_DATASOURCE_PASSWORD` |
| `database "db_scoring" does not exist` | La base no esta creada | Crearla en PostgreSQL |
| `Connection refused` a `localhost:5432` | PostgreSQL apagado o en otro host/puerto | Iniciar el servicio o ajustar `SPRING_DATASOURCE_URL` |
| Las variables "no se toman" | Se definieron en otra terminal, o se uso `.env` esperando que Spring lo lea | Definirlas en la misma terminal/IDE donde se ejecuta |
| Error de compilacion de version Java | JDK distinto de 21 | Instalar y usar JDK 21 |
| Puerto 8081 ocupado | Otro proceso lo usa | Cambiar `SERVER_PORT` |

### Ejecucion con Docker

```bash
# Construir imagen
docker build -t crediticio-auth .

# Ejecutar contenedor
docker run -p 8081:8081 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/db_scoring \
  -e SPRING_DATASOURCE_USERNAME=postgres \
  -e SPRING_DATASOURCE_PASSWORD=tu_password \
  -e JWT_SECRET=una-clave-secreta-de-al-menos-32-caracteres-para-256-bits \
  crediticio-auth
```

El Dockerfile usa multi-stage build (JDK 21 para compilar, JRE 21 Alpine para ejecutar), usuario no-root y healthcheck integrado.

### Swagger UI

Disponible en `http://localhost:8081/swagger-ui.html` — incluye esquema Bearer para autenticacion con JWT. Cada endpoint esta documentado con `@Operation`, `@ApiResponse` y `@Schema` en los DTOs.

## Endpoints

### Autenticacion (`/api/v1/auth`)

| Metodo | Ruta | Descripcion | Acceso |
|---|---|---|---|
| `POST` | `/login` | Login con email y contrasena → JWT + rol | Publico |
| `POST` | `/refresh` | Renueva un token valido | ADMIN, ANALISTA |
| `GET` | `/validate` | Valida token y retorna identidad + rol | ADMIN, ANALISTA |

### Usuarios (`/api/v1/usuarios`)

| Metodo | Ruta | Descripcion | Acceso |
|---|---|---|---|
| `POST` | `/` | Crea usuario con rol asignado | ADMIN |
| `GET` | `/` | Lista todos los usuarios | ADMIN |
| `GET` | `/{id}` | Detalle de un usuario | ADMIN (cualquiera), ANALISTA (solo propio) |
| `PATCH` | `/{id}/estado` | Activa o desactiva un usuario | ADMIN |
| `PATCH` | `/{id}/password` | Cambia la contrasena de un usuario | ADMIN (reset), ANALISTA (solo propia, requiere contrasena actual) |

### Matriz RBAC

| Endpoint | ADMIN | ANALISTA | Publico |
|---|---|---|---|
| `POST /auth/login` | — | — | Si |
| `POST /auth/refresh` | Si | Si | — |
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

Seed: `ADMIN`, `ANALISTA`.

### `usuarios`

| Columna | Tipo | Restriccion |
|---|---|---|
| `id_usuario` | SERIAL | PK |
| `nombre` | VARCHAR(150) | NOT NULL |
| `email` | VARCHAR(255) | UNIQUE NOT NULL |
| `password_hash` | VARCHAR(255) | NOT NULL |
| `id_rol` | INTEGER | FK → `roles.id_rol` |
| `activo` | BOOLEAN | DEFAULT TRUE |
| `fecha_creacion` | TIMESTAMP | DEFAULT CURRENT_TIMESTAMP |
| `fecha_modificacion` | TIMESTAMP | Nullable |

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
| `V105` | Seed del usuario administrador inicial |

Prefijo `V100+` para no colisionar con las migraciones del Motor de Scoring (`V1-V99`).

### Usuario administrador inicial (seed)

La migracion `V105` crea un usuario administrador para resolver el problema chicken-and-egg (el endpoint de crear usuarios requiere rol ADMIN, pero no existe ningun ADMIN inicial).

| Campo | Valor |
|---|---|
| Nombre | Administrador |
| Email | `admin@crediticio.com` |
| Contrasena | `Admin2026!` |
| Rol | ADMIN |

**Importante:** cambiar esta contrasena inmediatamente despues del primer login usando `PATCH /api/v1/usuarios/{id}/password`.

### Procedimiento almacenado

```sql
SELECT * FROM sp_resumen_usuarios_por_rol();
-- Retorna: rol_nombre | total_usuarios | activos | inactivos
```

Consulta no trivial con `LEFT JOIN`, `GROUP BY` y `FILTER` condicional.

## Flujo de autenticacion

```
┌──────────┐     POST /auth/login      ┌──────────────┐
│ Frontend │ ──────────────────────────→│ Auth Backend │
│          │ ←────────────────────────── │  (este repo) │
│          │     { token, rol }         └──────────────┘
│          │
│          │  Authorization: Bearer <token>
│          │ ──────────────────────────→ Auth Backend | Motor de Scoring
└──────────┘
```

1. El frontend envia `POST /auth/login` → recibe el JWT.
2. Incluye el JWT como `Authorization: Bearer <token>` en peticiones a ambos backends.
3. El Motor de Scoring valida el token de forma autonoma (misma `JWT_SECRET`) o via `GET /auth/validate`.

## Seguridad

### Medidas implementadas

- **Contrasenas:** hash BCrypt, nunca en texto plano. `password_hash` nunca se expone en respuestas.
- **JWT:** firmado con HMAC-SHA256, clave via variable de entorno, expiracion configurable.
- **RBAC:** por endpoint en Spring Security. Politica "solo propio" para ANALISTA en perfil y contrasena.
- **Cambio de contrasena:** ANALISTA debe verificar contrasena actual; ADMIN puede hacer reset.
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
| `UserServiceTest` | Unitario | 14 | createUser, getAllUsers, getUserById, toggleStatus, changePassword |
| `AuthServiceTest` | Unitario | 8 | login, refresh, validate (exito + errores) |
| `JwtTokenProviderTest` | Unitario | 7 | generate, validate, expired, malformed, null, wrong secret |
| `AuthFlowIntegrationTest` | Integracion | 25 | Login, CRUD, RBAC, cambio contrasena, tokens, usuarios desactivados |
| `AuthApplicationTests` | Contexto | 1 | Spring Boot context loads |

**Total: 55 tests.**

## Decisiones arquitectonicas (ADR)

| ADR | Decision |
|---|---|
| [ADR-001](docs/adr/ADR-001-arquitectura-hexagonal.md) | Hexagonal (Ports & Adapters) sobre Package by Layer |
| [ADR-002](docs/adr/ADR-002-autenticacion-jwt-stateless.md) | JWT Stateless sobre sesiones/OAuth2 |
| [ADR-003](docs/adr/ADR-003-base-de-datos-compartida.md) | BD compartida con prefijos Flyway diferenciados |

## CI/CD

GitHub Actions (`.github/workflows/ci.yml`): en cada push/PR a `main` ejecuta tests, construye el JAR y lo sube como artefacto con retencion de 7 dias.

## Coordinacion con el Motor de Scoring

- Comparten la misma BD (`db_scoring`) pero **no se llaman entre si directamente**.
- Cada backend gestiona sus propias tablas con rangos de migracion Flyway diferenciados.
- El frontend orquesta las peticiones a ambos backends.
