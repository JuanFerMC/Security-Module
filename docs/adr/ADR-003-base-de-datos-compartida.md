# ADR-003: Base de Datos Compartida entre Auth y Motor de Scoring

**Estado:** Aceptado  
**Fecha:** 2026-10-06  
**Autores:** Equipo de desarrollo

## Contexto

El sistema tiene dos backends:

1. **Auth** (este repositorio): gestiona `usuarios` y `roles`.
2. **Motor de Scoring**: gestiona solicitantes, reglas, evaluaciones y riesgo crediticio.

Se necesita decidir cómo organizar la persistencia considerando que:

- Ambos backends necesitan acceso a la información de usuarios (Auth para gestión, Motor para asociar evaluaciones a analistas).
- El equipo es pequeño y la infraestructura debe ser simple en esta fase.
- No hay requerimiento actual de escalar cada backend de forma independiente en términos de datos.

Se evaluaron tres alternativas:

1. **Base de datos por servicio** (database-per-service): máximo desacoplamiento pero requiere sincronización de datos de usuario entre bases (eventos, replicación). Complejidad operativa alta.
2. **Esquemas separados en la misma BD**: aislamiento lógico pero misma instancia PostgreSQL. Flyway necesita configuración por esquema.
3. **Misma base de datos y esquema, migraciones con prefijo diferenciado**: simple, sin sincronización, separación por convención de versionado Flyway.

## Decisión

Ambos backends comparten la **misma instancia y base de datos** PostgreSQL (`db_scoring`), con **rangos de versión diferenciados** en Flyway para evitar colisiones:

| Backend | Rango de migraciones | Tablas |
|---|---|---|
| Motor de Scoring | `V1__` a `V99__` | solicitantes, reglas, evaluaciones, etc. |
| Auth (este repo) | `V100__` en adelante | roles, usuarios |

Cada backend gestiona exclusivamente sus propias tablas. Las migraciones de Auth no tocan las tablas del Motor de Scoring y viceversa.

## Consecuencias

### Positivas

- Sin complejidad de sincronización de datos — el Motor de Scoring puede hacer `JOIN` directo con `usuarios` si lo necesita en el futuro.
- Una sola instancia PostgreSQL para administrar (backups, monitoreo, conexiones).
- El prefijo de versiones Flyway evita colisiones sin coordinación compleja entre equipos.
- Despliegue simple: no hay dependencias de orden entre backends al migrar.

### Negativas

- Acoplamiento a nivel de datos: si un backend modifica el esquema de sus tablas, puede afectar queries del otro backend (si existieran queries cruzadas).
- No se puede escalar la base de datos de forma independiente por backend.
- Riesgo de colisión si un backend excede su rango de versiones asignado (mitigable con convención y revisión en PR).
- Migrar a base de datos por servicio en el futuro requiere partir datos y sincronizar — coste de migración significativo.

## Actualizacion 2026-10-14: validacion de Flyway en BD compartida

Al arrancar este backend contra una `db_scoring` que ya tenia las migraciones V1-V5 del Motor de Scoring, Flyway fallaba con `Detected applied migration not resolved locally`, porque por defecto exige que toda migracion aplicada exista en el classpath.

Decision: configurar `spring.flyway.ignore-migration-patterns: "*:missing"` en `application.yml`. Las migraciones ajenas se ignoran; las propias (V100+) siguen validandose (checksum, orden). El Motor de Scoring debe aplicar la misma configuracion en su lado (ignorar `*:missing` para V100+).
