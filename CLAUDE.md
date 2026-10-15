# CLAUDE.md

Reglas de trabajo para asistir en el desarrollo de la **API de Autenticación y Gestión de Usuarios** del Sistema de Scoring de Riesgo Crediticio. Estas reglas son de obligatorio cumplimiento para cualquier trabajo realizado en este repositorio.

## 1. Descripción del proyecto

La API de Autenticación y Gestión de Usuarios es un backend independiente encargado de gestionar el ciclo de vida de los usuarios del sistema (registro, consulta, activación/desactivación), la asignación de roles, y la autenticación mediante tokens JWT. Este servicio expone endpoints REST que son consumidos por el frontend y consultados por el Motor de Scoring para validar la identidad y los permisos de cada petición.

### Relación con el ecosistema

Este backend forma parte de un sistema de tres componentes:

1. **Motor de Scoring** (`motor-scoring-crediticio`): backend de reglas de negocio, scoring y evaluaciones.
2. **API de Autenticación y Usuarios** (este repositorio): backend de identidad, roles y tokens.
3. **Frontend** (`credit-compass`): cliente web React/TanStack que consume ambos backends.

Los tres componentes comparten la misma base de datos PostgreSQL (`db_scoring`). Este backend es responsable de las tablas de usuarios y roles; el motor de scoring es responsable de las tablas de solicitantes, riesgo, reglas y evaluaciones.

## 2. Tecnologías

* Java 21
* Spring Boot (spring-boot-starter-parent) con Spring Web MVC, Spring Data JPA, Spring Validation, Spring Security y Spring Boot Actuator
* Maven (con Maven Wrapper)
* PostgreSQL (base de datos de ejecución, compartida con el motor de scoring)
* H2 (solo para pruebas, perfil `test`)
* Flyway (migraciones de base de datos)
* JSON Web Tokens — JWT (librería `jjwt` de io.jsonwebtoken)
* Spring Security (filtro JWT, autenticación stateless, hashing con BCrypt)
* springdoc-openapi (documentación OpenAPI/Swagger)
* Lombok
* Docker (previsto para el entorno de contenedores)

No se debe agregar, quitar ni actualizar dependencias o versiones sin justificación explícita ligada a una HU o a una instrucción directa.

## 3. Arquitectura general

* **Nivel de sistema**: la arquitectura del proyecto es un **monolito modular**. Todo este backend se despliega como una única aplicación, pero internamente está dividido en módulos de negocio independientes y desacoplados entre sí.
* **Organización estructural**: la organización del código sigue **Package by Feature**. Cada funcionalidad de negocio vive en su propio paquete de alto nivel bajo `com.crediticio.auth`, en lugar de agrupar el código por tipo técnico a nivel global.
* **Organización interna de cada módulo**: dentro de cada módulo se aplican los principios de **Ports & Adapters (arquitectura hexagonal)** para separar dominio, aplicación e infraestructura: el dominio y la aplicación definen contratos mediante `ports`, y la `infrastructure` provee los adaptadores concretos (entrada y salida) que implementan o consumen esos contratos.

No se debe introducir otro estilo arquitectónico (por ejemplo, Package by Layer a nivel global, microservicios internos, CQRS, event sourcing, etc.) sin que exista una justificación explícita y aprobación previa.

## 4. Módulos de negocio

Los módulos de negocio de este backend son:

* `users`: gestión del ciclo de vida de usuarios (creación, consulta, actualización, activación/desactivación).
* `roles`: gestión de roles del sistema y asignación a usuarios.
* `authentication`: autenticación de usuarios (login), emisión y validación de tokens JWT, y renovación de sesiones.

Componentes transversales:

* `config`: configuración general de la aplicación (seguridad, filtros JWT, OpenAPI, CORS, beans compartidos).
* `shared`: funcionalidades compartidas entre módulos (`exception`, `response`, `util`, `security`).

No se deben crear nuevos módulos de negocio ni componentes transversales que no estén contemplados en la HU actual sin aprobación explícita.

## 5. Organización interna de cada módulo

Cada módulo de negocio (`users`, `roles`, `authentication`) sigue esta estructura interna:

```
com.crediticio.auth/
├── users/
│   ├── domain/              # Entidad Usuario, enums, invariantes de negocio
│   ├── application/         # Casos de uso (crear, desactivar, consultar)
│   │   └── dto/             # DTO de entrada y salida
│   ├── ports/
│   │   ├── input/           # Interfaces de casos de uso
│   │   └── output/          # Interfaces de repositorio
│   └── infrastructure/
│       ├── input/           # UsuarioController (REST)
│       └── output/          # UsuarioJpaRepository, UsuarioJpaAdapter
├── roles/
│   ├── domain/
│   ├── application/
│   │   └── dto/
│   ├── ports/
│   │   ├── input/
│   │   └── output/
│   └── infrastructure/
│       ├── input/
│       └── output/
├── authentication/
│   ├── domain/              # Lógica de validación de credenciales
│   ├── application/         # Caso de uso: autenticar, renovar token
│   │   └── dto/             # LoginRequest, LoginResponse (con token y rol)
│   ├── ports/
│   │   ├── input/           # AuthInputPort
│   │   └── output/          # TokenProviderPort, PasswordEncoderPort
│   └── infrastructure/
│       ├── input/           # AuthController (REST)
│       └── output/          # JwtTokenProvider, BcryptPasswordEncoder
├── config/
│   ├── SecurityConfig.java        # Spring Security (stateless, filtro JWT)
│   ├── JwtAuthenticationFilter.java  # Filtro que valida el token en cada request
│   ├── CorsConfig.java             # Configuración CORS
│   └── OpenApiConfig.java          # Swagger/OpenAPI
└── shared/
    ├── exception/           # GlobalExceptionHandler, excepciones de dominio
    ├── response/            # ApiError con errorCode, message, details, traceId
    └── security/            # Utilidades JWT compartidas
```

Toda nueva clase debe ubicarse en la capa que le corresponde según esta organización. No se debe mezclar responsabilidades de capas distintas en una misma clase.

## 6. Principios de diseño, GRASP y patrones de diseño

Aplicar de forma pertinente (sin sobrediseñar). Ningún principio o patrón de esta sección es de uso obligatorio: se utilizan únicamente cuando resuelven una necesidad concreta y mejoran la separación de responsabilidades, el acoplamiento, la cohesión o la extensibilidad del código.

### Principios de diseño — SOLID

* **SRP**: cada clase tiene una única responsabilidad y una única razón de cambio.
* **OCP**: extender comportamiento mediante nuevas implementaciones de puertos/interfaces, evitando modificar código estable ya probado.
* **LSP**: las implementaciones de una interfaz de puerto deben ser sustituibles sin alterar el comportamiento esperado por quien las consume.
* **ISP**: los puertos (`ports/input`, `ports/output`) deben ser específicos y pequeños, no interfaces "todo en uno".
* **DIP**: la capa de aplicación y dominio dependen de abstracciones (`ports`), nunca de implementaciones concretas de infraestructura.

### Patrones de asignación de responsabilidades — GRASP

* **Information Expert**: asignar la responsabilidad a la clase que tiene la información necesaria para cumplirla.
* **Creator**: quien agrega, contiene o usa estrechamente los datos de un objeto es responsable de crearlo.
* **Controller**: los adaptadores de entrada (controladores REST) delegan la lógica a los servicios de aplicación; no contienen reglas de negocio.
* **Low Coupling**: mantener dependencias mínimas y explícitas entre módulos y capas, mediadas por `ports`.
* **High Cohesion**: cada clase y módulo se mantiene enfocado en una responsabilidad claramente relacionada.
* **Indirection**: usar los puertos como intermediarios para desacoplar la aplicación de la infraestructura concreta.
* **Polymorphism**: variar el comportamiento según el tipo mediante distintas implementaciones de una misma interfaz de puerto, en lugar de condicionales por tipo.
* **Protected Variations**: aislar los puntos de variación previsibles (persistencia, proveedor de tokens, algoritmo de hashing) detrás de `ports`.

### Patrones de diseño

Se consideran válidos para la arquitectura y el dominio actual únicamente los siguientes patrones, aplicados solo cuando exista una necesidad concreta:

* **Repository**: para abstraer el acceso a persistencia detrás de un puerto de salida (`ports/output`).
* **Adapter**: para implementar los puertos en la capa de `infrastructure`, conectando dominio/aplicación con tecnologías externas.
* **Strategy**: únicamente cuando exista una necesidad real de encapsular comportamientos intercambiables (por ejemplo, distintos proveedores de tokens o algoritmos de hashing).

No se deben aplicar patrones adicionales sin justificación explícita.

## 7. Uso de DTO

* Los controladores (`infrastructure/input`) reciben y devuelven exclusivamente DTO definidos en `application/dto`, nunca entidades de dominio directamente.
* Los DTO de entrada deben validarse con Bean Validation (`spring-boot-starter-validation`) cuando corresponda.
* La conversión entre DTO y entidades de dominio ocurre en la capa de aplicación (o en un mapper dedicado dentro de esa capa), no en el dominio ni en el controlador.
* No exponer campos internos o sensibles del dominio en los DTO de salida (en particular: nunca exponer `password_hash`, tokens internos, ni campos de auditoría que no sean necesarios para el cliente).

## 8. Separación entre dominio, aplicación e infraestructura

* El **dominio** no depende de Spring, JPA, ni de ningún framework de infraestructura. Contiene entidades, value objects y reglas de negocio puras.
* La **aplicación** orquesta el dominio a través de los puertos, sin conocer detalles de infraestructura.
* La **infraestructura** implementa los puertos de salida (persistencia, proveedor JWT, encoder de contraseñas) y expone los puertos de entrada (controladores REST, filtro de autenticación).
* Nunca se debe hacer referencia inversa: el dominio no debe importar clases de `infrastructure`, y la aplicación no debe importar detalles concretos de `infrastructure` (solo los `ports`).

## 9. Seguridad y autenticación

### JWT (JSON Web Tokens)

* La autenticación es **stateless**: no se usan sesiones del lado del servidor. El estado de autenticación se transporta íntegramente en el token JWT.
* El token JWT contiene como mínimo: `sub` (email o id del usuario), `rol` (nombre del rol), `iat` (issued at) y `exp` (expiración).
* La clave secreta para firmar los tokens se inyecta vía variable de entorno (`JWT_SECRET`), nunca se hardcodea en el código fuente.
* El tiempo de expiración del token se configura vía variable de entorno (`JWT_EXPIRATION_MS`), con un valor por defecto razonable (por ejemplo, 3600000 ms = 1 hora).
* La emisión y validación de tokens están aisladas detrás de un puerto de salida (`TokenProviderPort`), permitiendo cambiar la implementación sin afectar el dominio.

### Contraseñas

* Las contraseñas se almacenan **exclusivamente como hash** usando BCrypt. Nunca se almacenan en texto plano.
* El hashing está aislado detrás de un puerto de salida (`PasswordEncoderPort`).

### Filtro de autenticación

* Un filtro (`JwtAuthenticationFilter`) intercepta cada petición HTTP, extrae el token del header `Authorization: Bearer <token>`, lo valida, y establece el contexto de seguridad de Spring.
* Los endpoints públicos (login, health check, Swagger) se excluyen explícitamente del filtro.

### RBAC (Control de acceso basado en roles)

* El acceso a los endpoints se controla mediante el rol del usuario autenticado.
* La matriz de permisos mínima:

| Endpoint | ADMIN | ANALISTA | Público |
|---|---|---|---|
| `POST /api/v1/auth/login` | — | — | ✓ |
| `POST /api/v1/auth/refresh` | ✓ | ✓ | — |
| `POST /api/v1/usuarios` | ✓ | — | — |
| `GET /api/v1/usuarios` | ✓ | — | — |
| `GET /api/v1/usuarios/{id}` | ✓ | ✓ (solo propio) | — |
| `PATCH /api/v1/usuarios/{id}/estado` | ✓ | — | — |
| `GET /api/v1/auth/validate` | ✓ | ✓ | — |

* El RBAC se aplica a nivel de endpoint en el backend, no solo en la interfaz.

## 10. Reglas para PostgreSQL y Flyway

* PostgreSQL es la base de datos objetivo de ejecución. **Se comparte la misma instancia y base de datos** (`db_scoring`) con el motor de scoring.
* Las migraciones Flyway de este backend gestionan exclusivamente las tablas de su dominio (`usuarios`, `roles`). No deben tocar las tablas del motor de scoring.
* Para evitar colisiones de versionamiento con las migraciones del motor de scoring, las migraciones de este backend usan un **prefijo diferenciado**: `V100__`, `V101__`, `V102__`, etc. (las del motor de scoring usan `V1__` a `V99__`).
* Todo cambio de esquema se implementa mediante una nueva migración de Flyway versionada en `src/main/resources/db/migration`, siguiendo la convención `V{version}__{descripcion}.sql`.
* Nunca modificar una migración de Flyway ya aplicada/versionada previamente.
* `spring.jpa.hibernate.ddl-auto` no debe usarse para gestionar el esquema en producción. En el perfil `test` puede usarse `create-drop` sobre H2 con Flyway deshabilitado.

## 11. Modelo de datos: tablas de este backend

El modelo relacional de este backend contempla las siguientes tablas:

1. `roles`: catálogo de roles del sistema (`ADMIN`, `ANALISTA`).
   - `id_rol` (PK, SERIAL)
   - `nombre` (VARCHAR UNIQUE NOT NULL)

2. `usuarios`: usuarios registrados en el sistema.
   - `id_usuario` (PK, SERIAL)
   - `nombre` (VARCHAR NOT NULL)
   - `email` (VARCHAR UNIQUE NOT NULL)
   - `password_hash` (VARCHAR NOT NULL)
   - `id_rol` (FK → `roles.id_rol`, NOT NULL)
   - `activo` (BOOLEAN DEFAULT TRUE)
   - `fecha_creacion` (TIMESTAMP DEFAULT CURRENT_TIMESTAMP)
   - `fecha_modificacion` (TIMESTAMP, nullable)

No se deben crear tablas adicionales, ni modificar tablas del motor de scoring, sin que la HU en curso lo requiera explícitamente y sin aprobación.

## 12. Endpoints del backend

Los endpoints previstos son:

### Autenticación (`/api/v1/auth`)
* `POST /api/v1/auth/login` — Autentica un usuario con email y contraseña; devuelve un token JWT con el rol.
* `POST /api/v1/auth/refresh` — Renueva un token JWT válido antes de su expiración.
* `GET /api/v1/auth/validate` — Valida un token JWT y devuelve la identidad y rol del usuario (consumido por el motor de scoring para verificar peticiones).

### Usuarios (`/api/v1/usuarios`)
* `POST /api/v1/usuarios` — Crea un nuevo usuario con rol asignado (solo ADMIN).
* `GET /api/v1/usuarios` — Lista todos los usuarios (solo ADMIN).
* `GET /api/v1/usuarios/{id}` — Consulta el detalle de un usuario (ADMIN o el propio usuario).
* `PATCH /api/v1/usuarios/{id}/estado` — Activa o desactiva un usuario (solo ADMIN).

No se deben implementar endpoints adicionales sin que la HU lo requiera.

## 13. Variables de entorno

| Variable | Descripción | Ejemplo |
|---|---|---|
| `SPRING_DATASOURCE_URL` | URL JDBC de la BD compartida | `jdbc:postgresql://host:5432/db_scoring` |
| `SPRING_DATASOURCE_USERNAME` | Usuario de la BD | `db_scoring_user` |
| `SPRING_DATASOURCE_PASSWORD` | Contraseña de la BD | (secreto) |
| `JWT_SECRET` | Clave secreta para firmar tokens | (mínimo 256 bits, secreto) |
| `JWT_EXPIRATION_MS` | Tiempo de expiración del token en ms | `3600000` (1 hora) |
| `CORS_ALLOWED_ORIGINS` | Orígenes permitidos (frontend) | `http://localhost:3000` |
| `SERVER_PORT` | Puerto del servidor | `8081` (para no colisionar con el motor de scoring en `8080`) |

### Reglas de documentación de configuración y despliegue

* `JWT_SECRET` y `SPRING_DATASOURCE_PASSWORD` **no tienen valor por defecto**: la app no arranca (o falla al firmar tokens) sin ellas. Siempre deben figurar como obligatorias en README y `.env.example`.
* `JWT_SECRET` no viene de ningún servicio: la genera quien despliega (aleatoria, 32+ caracteres), es distinta por entorno y debe coincidir con la del motor de scoring si este valida tokens por su cuenta.
* Spring Boot **no lee `.env` automáticamente**. Las variables se definen en el entorno del proceso (terminal, IDE, Docker `-e`, servidor). `.env` está en `.gitignore`; la plantilla versionada es `.env.example`.
* Los secretos nunca se escriben en código, commits, logs ni chat. Los valores reales se configuran en el entorno de destino.
* El CI solo compila y prueba contra H2 y no despliega; el despliegue requiere configurar las variables en el destino.
* **Todo cambio que agregue, renombre o elimine una variable de entorno, o cambie el procedimiento de arranque/despliegue, debe actualizar en el mismo cambio:** este archivo (sección 13), `README.md` (Configuración, Primer arranque, Despliegue, Problemas comunes) y `.env.example`.
* Antes de dar por documentado un cambio de arranque, verificar los pasos del README en una terminal limpia (sin variables previas).

## 14. Reglas de validación y pruebas

* Toda nueva funcionalidad debe incluir pruebas automatizadas (unitarias y/o de integración) que cubran el comportamiento esperado y los criterios de aceptación de la HU.
* Las pruebas de integración usan el perfil `test` con H2 en memoria.
* Las pruebas deben ejecutarse antes de dar por terminada una HU (`./mvnw test` o `./mvnw.cmd clean test`).
* No se deben eliminar, comentar, deshabilitar ni modificar pruebas existentes con el fin de ocultar errores.
* Datos sensibles en pruebas: usar contraseñas de prueba genéricas, nunca credenciales reales.

## 15. Reglas de Git y ramas

* Todo trabajo de una HU se realiza en una rama dedicada creada a partir de `main` (por ejemplo, `feature/<nombre-corto-de-la-hu>`).
* No se debe trabajar directamente commits de código de HU sobre `main`.
* Cada rama debe mantenerse enfocada en una sola HU o tarea.

## 16. No modificar `main` directamente

Está prohibido modificar la rama `main` directamente. Todo cambio de código llega a `main` mediante el flujo de ramas y revisión definido por el equipo, nunca mediante commits directos sobre `main`.

## 17. Autorización explícita para commit y push

No se debe ejecutar `git commit` ni `git push` bajo ninguna circunstancia sin la autorización explícita del responsable en la conversación, para esa acción concreta. Una autorización dada para un commit/push no habilita commits o pushes futuros; debe solicitarse de nuevo cada vez.

## 18. No implementar fuera del alcance de la HU actual

No se debe implementar ninguna funcionalidad, endpoint, entidad, regla o mejora que no esté explícitamente contemplada en la Historia de Usuario (HU) que se está trabajando en el momento. Cualquier necesidad adicional detectada se reporta y se espera indicación, no se implementa de forma proactiva.

## 19. No ocultar errores mediante pruebas

No se deben eliminar ni modificar pruebas existentes para hacer que un build pase ocultando un error real. Los errores detectados por las pruebas deben resolverse en el código de producción.

## 20. Flujo obligatorio para cada Historia de Usuario (HU)

Para cada HU se debe seguir, en orden, el siguiente flujo:

1. Leer la especificación completa de la HU.
2. Revisar los criterios de aceptación de la HU.
3. Analizar el estado actual del proyecto relevante para la HU (estructura, código existente, dependencias).
4. Proponer un plan de implementación.
5. Esperar la aprobación explícita del plan antes de escribir o modificar código.
6. Implementar el plan aprobado.
7. Crear o actualizar las pruebas automatizadas correspondientes.
8. Ejecutar las pruebas y verificar que pasen.
9. Verificar que se cumplen todos los criterios de aceptación de la HU.
10. Revisar los cambios realizados (diff) antes de darlos por finalizados.
11. Actualizar el progreso del proyecto (por ejemplo, en `claude-progress.md` cuando exista).

No se debe saltar, reordenar de forma que pierda su intención, ni omitir ninguno de estos pasos.

## 21. Ambigüedad en los requisitos

Si durante el análisis de una HU se identifica una ambigüedad importante, se debe **detener el trabajo** y solicitar aclaración explícita antes de continuar, en lugar de asumir o inventar el comportamiento esperado.

## 22. Revisar antes de modificar

Antes de modificar cualquier archivo existente, se debe revisar su implementación actual (y la de los archivos relacionados) para entender el contexto y evitar romper comportamiento existente o duplicar lógica.

## 23. Cambios pequeños y acotados a la HU

Los cambios realizados deben ser pequeños y estar directamente relacionados con la HU en curso. No se deben incluir refactorizaciones, limpiezas o mejoras no solicitadas junto con el cambio de la HU.

## 24. Coordinación con el motor de scoring

* Este backend y el motor de scoring comparten la misma base de datos pero **no se llaman entre sí directamente** en esta fase. El frontend es quien orquesta las peticiones a ambos backends.
* El flujo de autenticación previsto es:
  1. El frontend envía `POST /api/v1/auth/login` a **este backend** → recibe el JWT.
  2. El frontend incluye el JWT en el header `Authorization: Bearer <token>` en cada petición a **ambos backends**.
  3. El motor de scoring puede validar el token de forma autónoma (si comparte la misma `JWT_SECRET`) o consultando `GET /api/v1/auth/validate` de este backend.
* Las tablas de cada backend son independientes y gestionadas por sus propias migraciones Flyway con rangos de versión diferenciados.

## Reglas adicionales importantes

* No inventar requisitos funcionales que no estén especificados.
* No implementar funcionalidades de forma automática o anticipada.
* No hacer `commit` ni `push` sin autorización explícita (ver regla 17).
* No cambiar la arquitectura existente sin justificarlo y sin aprobación explícita.
* No ejecutar cambios adicionales fuera de lo solicitado en la interacción actual.
