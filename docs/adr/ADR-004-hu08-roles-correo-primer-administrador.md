# ADR-004: Roles de la HU08, correo normalizado y primer administrador

## Estado

Aceptado — 2026-10-14

## Contexto

La verificacion de HU08 (`docs/VERIFICACION_HU08_HU09.md`) encontro que:

- Los roles del sistema eran `ADMIN` y `ANALISTA`, y la HU exige `ADMINISTRADOR_RIESGO` y `ANALISTA_CREDITO`.
- El correo no se normalizaba y la unicidad distinguia mayusculas.
- La contrasena minima era 8 y la HU exige 12.
- El primer administrador salia de un seed en la migracion V105 con correo y contrasena fijos y publicos, contra RF09, RF10 y RNF06.

Los demas componentes del ecosistema (Motor de Scoring y frontend) no dependian aun de los nombres de rol: el Motor tiene `permitAll` y el frontend solo los menciona en su plan.

## Decision

1. **Roles:** adoptar los nombres de la HU. Migracion V106 los renombra sin cambiar los ids. Constante `RolSistema` con la lista blanca; el servicio rechaza cualquier otro rol (400 `ROL_NOT_ALLOWED`).
2. **Correo:** normalizar (minusculas, sin espacios laterales) en alta y login, e indice unico sobre `lower(email)` como garantia en BD. Un duplicado responde **409** (decision del equipo).
3. **Contrasena:** 12 a 72 caracteres (72 es el limite efectivo de BCrypt), tambien al cambiarla.
4. **Primer administrador:** `AdminBootstrapRunner` lo crea al arrancar con `INIT_ADMIN_EMAIL` e `INIT_ADMIN_PASSWORD`, solo si no existe ningun `ADMINISTRADOR_RIESGO`. No hay endpoint publico. V106 elimina el seed viejo unicamente si conserva la contrasena publica original.
5. **Trazabilidad:** columna `creado_por` con el correo del actor (o `BOOTSTRAP`), y log de creacion sin datos sensibles.

## Alternativas consideradas

- **Mantener `ADMIN`/`ANALISTA`:** incumple RF06 y obligaria a traducir nombres en cada integracion. Descartada.
- **Comando operativo aparte para el primer administrador:** mas explicito, pero exige un artefacto de despliegue adicional. Las variables de entorno encajan con la configuracion externa ya usada (RNF06).
- **Dejar el seed y exigir cambio de contrasena:** deja una credencial publica valida mientras no se cambie. Descartada.

## Consecuencias

- El rol en el JWT y en `GET /auth/validate` cambia de nombre: el Motor de Scoring y el frontend deben usar los nuevos al integrarse.
- Un entorno que ya corrio V105 queda sin administrador tras V106 hasta que se definan `INIT_ADMIN_*` y se reinicie.
- Las pruebas con H2 no ejecutan Flyway; la migracion V106 se valido contra PostgreSQL real.
