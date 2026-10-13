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

- JDK 21
- PostgreSQL con la base de datos `db_scoring` creada
- Docker (opcional, para ejecucion en contenedor)

## Configuracion

### Variables de entorno

| Variable | Descripcion | Default |
|---|---|---|
| `SPRING_DATASOURCE_URL` | URL JDBC | `jdbc:postgresql://localhost:5432/db_scoring` |
| `SPRING_DATASOURCE_USERNAME` | Usuario BD | `postgres` |
| `SPRING_DATASOURCE_PASSWORD` | Contrasena BD | — |
| `JWT_SECRET` | Clave para firmar tokens (min. 256 bits) | — |
| `JWT_EXPIRATION_MS` | Expiracion del token en ms | `3600000` (1 hora) |
| `CORS_ALLOWED_ORIGINS` | Origenes permitidos (separados por coma) | `http://localhost:3000` |
| `SERVER_PORT` | Puerto del servidor | `8081` |

### Ejecucion local

```bash
# Clonar
git clone https://github.com/Ulisesorozcovi/crediticio.auth.git
cd crediticio.auth

# Configurar variables (ejemplo)
export SPRING_DATASOURCE_PASSWORD=tu_password
export JWT_SECRET=una-clave-secreta-de-al-menos-32-caracteres-para-256-bits

# Ejecutar
./mvnw spring-boot:run        # Linux/Mac
.\mvnw.cmd spring-boot:run    # Windows
```

La aplicacion arranca en `http://localhost:8081`. Flyway ejecuta las migraciones automaticamente al iniciar.

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
