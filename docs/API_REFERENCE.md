# API Reference — crediticio.auth

**Base URL:** `http://localhost:8081/api/v1`  
**Swagger UI:** `http://localhost:8081/swagger-ui.html`  
**Content-Type:** `application/json`  
**Autenticacion:** JWT Bearer token en header `Authorization`

---

## Autenticacion

Todos los endpoints protegidos requieren el header:

```
Authorization: Bearer <token>
```

Al iniciar sesion (`POST /auth/login`) se reciben **dos tokens**: un *access token* JWT de **15 minutos** que se envia en cada peticion, y un *refresh token* opaco de **un solo uso** (maximo 7 dias) que sirve unicamente para obtener un par nuevo con `POST /auth/refresh` o cerrar la sesion con `POST /auth/logout`.

### Flujo recomendado en el frontend

1. **Login:** `POST /auth/login` con email y contrasena → guardar `token` (access) y `refreshToken`. Preferir memoria para el access token; tratar el refresh token como un secreto.
2. **Peticiones:** incluir `Authorization: Bearer <token>` en cada request a Auth y Motor de Scoring.
3. **Renovar:** cuando el access token venza (401) o falten pocos segundos (`expiresIn`), llamar `POST /auth/refresh` con `{ "refreshToken": "<ultimo>" }` y **reemplazar ambos tokens**. El refresh token anterior deja de servir.
4. **No reutilizar:** nunca reenviar un refresh token ya canjeado ni disparar dos renovaciones a la vez con el mismo: la segunda se interpreta como reutilizacion y **revoca la sesion**.
5. **Logout:** `POST /auth/logout` con el refresh token y descartar ambos tokens.
6. **Errores 401** en refresh: redirigir al login. **403:** el usuario no tiene el rol requerido.

---

## Endpoints

### POST /auth/login

Autentica un usuario con email y contrasena. Retorna un access token JWT (15 minutos), un refresh token y el rol.

**Acceso:** Publico (no requiere token)

**Request:**

```json
{
  "email": "admin@crediticio.com",
  "password": "ClaveSegura2026!"
}
```

| Campo | Tipo | Validacion |
|---|---|---|
| `email` | string | Requerido, formato email valido (no distingue mayusculas) |
| `password` | string | Requerido, no vacio |

**Response 200:**

```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "q3k2Vf0o1xk3wH...",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "rol": "ADMINISTRADOR_RIESGO"
}
```

| Campo | Tipo | Descripcion |
|---|---|---|
| `token` | string | Access token JWT. Incluir como `Bearer <token>` en peticiones protegidas |
| `refreshToken` | string | Refresh token opaco de un solo uso (maximo 7 dias) |
| `tokenType` | string | Siempre `Bearer` |
| `expiresIn` | number | Segundos de vigencia del access token (900) |
| `rol` | string | `ADMINISTRADOR_RIESGO` o `ANALISTA_CREDITO` |

Claims del access token: `sub` (id de usuario), `email`, `rol`, `iss`, `aud`, `iat`, `exp`, `jti`.

**Errores:**

| HTTP | errorCode | Causa |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Email vacio, formato invalido, password vacio |
| 401 | `INVALID_CREDENTIALS` | Credenciales invalidas. **No se indica cual fallo** (usuario inexistente, contrasena incorrecta o usuario desactivado responden igual) |

---

### POST /auth/refresh

Canjea un refresh token valido por un **nuevo access token y un nuevo refresh token** (rotacion). El refresh token usado queda invalidado en la misma operacion.

**Acceso:** Publico. No requiere `Authorization`; se autentica con el refresh token (el access token puede haber vencido).

**Request:**

```json
{ "refreshToken": "q3k2Vf0o1xk3wH..." }
```

**Response 200:** igual que `POST /auth/login` (nuevos `token` y `refreshToken`).

**Errores:**

| HTTP | errorCode | Causa |
|---|---|---|
| 400 | `MALFORMED_REQUEST` | El cuerpo no es JSON valido |
| 401 | `INVALID_CREDENTIALS` | Refresh token ausente, vacio, de longitud imposible (> 512), desconocido, vencido, revocado, ya usado, o usuario desactivado. El mensaje no distingue la causa |

**Reutilizacion:** presentar un refresh token que ya fue rotado revoca **toda la sesion** (incluido el token vigente de esa sesion); el usuario debe iniciar sesion de nuevo. Dos renovaciones simultaneas con el mismo token: solo una tiene exito.

---

### POST /auth/logout

Invalida la sesion (todos los refresh tokens de esa sesion). Idempotente: un token desconocido o ya cerrado tambien responde 204.

**Acceso:** Publico (se autentica con el refresh token).

**Request:**

```json
{ "refreshToken": "q3k2Vf0o1xk3wH..." }
```

**Response 204:** sin cuerpo.

| HTTP | errorCode | Causa |
|---|---|---|
| 400 | `MALFORMED_REQUEST` | El cuerpo no es JSON valido |

> El access token ya emitido sigue siendo valido hasta su vencimiento (maximo 15 minutos); no hay lista de revocacion de access tokens.

---

### GET /auth/validate

Valida un JWT y retorna la identidad del usuario consultando la BD. Consumido internamente por el Motor de Scoring para verificar peticiones.

**Acceso:** ADMINISTRADOR_RIESGO, ANALISTA_CREDITO

**Request:**

```
Header: Authorization: Bearer <token>
Body: (vacio)
```

**Response 200:**

```json
{
  "userId": 1,
  "email": "admin@crediticio.com",
  "rol": "ADMINISTRADOR_RIESGO"
}
```

| Campo | Tipo | Descripcion |
|---|---|---|
| `userId` | number | ID del usuario en la tabla `usuarios` |
| `email` | string | Email del usuario |
| `rol` | string | Rol actual del usuario |

**Errores:**

| HTTP | errorCode | Causa |
|---|---|---|
| 401 | `INVALID_CREDENTIALS` | Token invalido, expirado, o usuario no encontrado en BD |

---

### POST /usuarios

Crea un nuevo usuario con un rol asignado. La contrasena se almacena como hash BCrypt.

**Acceso:** Solo ADMINISTRADOR_RIESGO

**Request:**

```json
{
  "nombre": "Juan Perez",
  "email": "juan.perez@ejemplo.com",
  "password": "Segura123!",
  "idRol": 2
}
```

| Campo | Tipo | Validacion |
|---|---|---|
| `nombre` | string | Requerido, max 150 caracteres |
| `email` | string | Requerido, formato email, max 255, unico en el sistema |
| `password` | string | Requerido, de 12 a 72 caracteres |
| `idRol` | number | Requerido. Debe corresponder a `ADMINISTRADOR_RIESGO` o `ANALISTA_CREDITO` (ids segun la tabla `roles`) |

> El `email` se guarda normalizado (minusculas) y es unico sin distinguir mayusculas. El campo `creado_por` registra al administrador que creo el usuario.

**Response 201:**

```json
{
  "id": 3,
  "nombre": "Juan Perez",
  "email": "juan.perez@ejemplo.com",
  "rolNombre": "ANALISTA_CREDITO",
  "activo": true,
  "fechaCreacion": "2026-10-07T14:30:00",
  "fechaModificacion": null
}
```

> Nota: `password_hash` nunca se expone en ninguna respuesta.

**Errores:**

| HTTP | errorCode | Causa |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Campos vacios, email invalido, password fuera de 12-72 chars, idRol null. `details` contiene errores por campo |
| 400 | `ROL_NOT_ALLOWED` | El rol resuelto no es `ADMINISTRADOR_RIESGO` ni `ANALISTA_CREDITO` |
| 401 | `UNAUTHORIZED` | Sin token |
| 403 | `ACCESS_DENIED` | El usuario no es ADMINISTRADOR_RIESGO |
| 400 | `ROL_NOT_FOUND` | El `idRol` no existe en la tabla roles (entrada invalida) |
| 400 | `MALFORMED_REQUEST` | Cuerpo que no es JSON valido o con un tipo incorrecto (por ejemplo `idRol` como texto) |
| 409 | `DUPLICATE_EMAIL` | Ya existe un usuario con ese email |

---

### GET /usuarios

Lista todos los usuarios del sistema.

**Acceso:** Solo ADMINISTRADOR_RIESGO

**Request:**

```
Header: Authorization: Bearer <token>
Body: (vacio)
```

**Response 200:**

```json
[
  {
    "id": 1,
    "nombre": "Admin Principal",
    "email": "admin@crediticio.com",
    "rolNombre": "ADMINISTRADOR_RIESGO",
    "activo": true,
    "fechaCreacion": "2026-10-01T10:00:00",
    "fechaModificacion": null
  },
  {
    "id": 2,
    "nombre": "Ana Garcia",
    "email": "ana.garcia@ejemplo.com",
    "rolNombre": "ANALISTA_CREDITO",
    "activo": true,
    "fechaCreacion": "2026-10-05T09:15:00",
    "fechaModificacion": "2026-10-06T11:30:00"
  }
]
```

**Errores:**

| HTTP | errorCode | Causa |
|---|---|---|
| 401 | `UNAUTHORIZED` | Sin token |
| 403 | `ACCESS_DENIED` | El usuario no es ADMINISTRADOR_RIESGO |

---

### GET /usuarios/{id}

Consulta el detalle de un usuario por su ID.

**Acceso:** ADMINISTRADOR_RIESGO (cualquier usuario), ANALISTA_CREDITO (solo su propio perfil)

**Request:**

```
Header: Authorization: Bearer <token>
URL: /api/v1/usuarios/2
Body: (vacio)
```

**Response 200:**

```json
{
  "id": 2,
  "nombre": "Ana Garcia",
  "email": "ana.garcia@ejemplo.com",
  "rolNombre": "ANALISTA_CREDITO",
  "activo": true,
  "fechaCreacion": "2026-10-05T09:15:00",
  "fechaModificacion": null
}
```

> **Nota para el frontend:** para obtener el perfil del usuario logueado, usar `GET /auth/validate` para obtener el `userId`, luego `GET /usuarios/{userId}`.

**Errores:**

| HTTP | errorCode | Causa |
|---|---|---|
| 401 | `UNAUTHORIZED` | Sin token |
| 403 | `ACCESS_DENIED` | ANALISTA_CREDITO intento consultar el perfil de otro usuario |
| 404 | `USER_NOT_FOUND` | No existe usuario con ese ID |

---

### PATCH /usuarios/{id}/estado

Activa o desactiva un usuario. Un usuario desactivado no puede hacer login.

**Acceso:** Solo ADMINISTRADOR_RIESGO

**Request:**

```json
{
  "activo": false
}
```

| Campo | Tipo | Validacion |
|---|---|---|
| `activo` | boolean | Requerido. `true` = activar, `false` = desactivar |

**Response 200:**

```json
{
  "id": 2,
  "nombre": "Ana Garcia",
  "email": "ana.garcia@ejemplo.com",
  "rolNombre": "ANALISTA_CREDITO",
  "activo": false,
  "fechaCreacion": "2026-10-05T09:15:00",
  "fechaModificacion": "2026-10-07T16:00:00"
}
```

**Errores:**

| HTTP | errorCode | Causa |
|---|---|---|
| 401 | `UNAUTHORIZED` | Sin token |
| 403 | `ACCESS_DENIED` | El usuario no es ADMINISTRADOR_RIESGO |
| 404 | `USER_NOT_FOUND` | No existe usuario con ese ID |

---

### PATCH /usuarios/{id}/password

Cambia la contrasena de un usuario.

- **ADMINISTRADOR_RIESGO:** puede cambiar la contrasena de cualquier usuario (reset administrativo). No necesita enviar `currentPassword`.
- **ANALISTA_CREDITO:** solo puede cambiar su propia contrasena. Debe enviar `currentPassword` para verificar identidad.

**Acceso:** ADMINISTRADOR_RIESGO, ANALISTA_CREDITO

**Request (ADMINISTRADOR_RIESGO reseteando contrasena de otro usuario):**

```json
{
  "newPassword": "NuevaClave123!"
}
```

**Request (ANALISTA_CREDITO cambiando su propia contrasena):**

```json
{
  "currentPassword": "MiClaveActual1!",
  "newPassword": "MiClaveNueva1!"
}
```

| Campo | Tipo | Validacion |
|---|---|---|
| `currentPassword` | string | Requerido solo si no es ADMINISTRADOR_RIESGO. Se verifica contra el hash actual |
| `newPassword` | string | Requerido, de 12 a 72 caracteres |

**Response 204:** (sin cuerpo — la contrasena se cambio exitosamente)

**Errores:**

| HTTP | errorCode | Causa |
|---|---|---|
| 400 | `VALIDATION_ERROR` | `newPassword` vacia o fuera de 12-72 caracteres |
| 401 | `UNAUTHORIZED` | Sin token |
| 401 | `INVALID_CREDENTIALS` | `currentPassword` incorrecta o no proporcionada (ANALISTA_CREDITO) |
| 403 | `ACCESS_DENIED` | ANALISTA_CREDITO intento cambiar contrasena de otro usuario |
| 404 | `USER_NOT_FOUND` | No existe usuario con ese ID |

---

## Formato de errores

Todas las respuestas de error usan el mismo formato:

```json
{
  "errorCode": "VALIDATION_ERROR",
  "message": "Error de validacion en los datos de entrada",
  "details": {
    "email": "must not be blank",
    "password": "size must be between 12 and 72"
  },
  "traceId": "550e8400-e29b-41d4-a716-446655440000",
  "timestamp": "2026-10-07T14:30:00"
}
```

| Campo | Tipo | Siempre presente | Descripcion |
|---|---|---|---|
| `errorCode` | string | Si | Codigo de error para logica condicional en el frontend |
| `message` | string | Si | Mensaje legible para mostrar al usuario |
| `details` | object | No | Solo en `VALIDATION_ERROR`. Mapa campo → mensaje de error |
| `traceId` | string | Si | UUID para rastreo en logs del backend |
| `timestamp` | string | Si | ISO 8601 |

### Catalogo de codigos de error

> Todas las respuestas de error (incluidas 401 y 403) se envian como `application/json;charset=UTF-8`. Un cuerpo que no es JSON valido responde 400 `MALFORMED_REQUEST`, nunca 401.

| errorCode | HTTP | Descripcion |
|---|---|---|
| `VALIDATION_ERROR` | 400 | Datos de entrada invalidos. Ver `details` para errores por campo |
| `UNAUTHORIZED` | 401 | Token JWT ausente, invalido o expirado |
| `INVALID_CREDENTIALS` | 401 | Email/contrasena incorrectos, usuario inactivo, o contrasena actual incorrecta en cambio de contrasena |
| `ACCESS_DENIED` | 403 | El rol del usuario no tiene permiso para este endpoint |
| `USER_NOT_FOUND` | 404 | No existe usuario con el ID proporcionado |
| `ROL_NOT_FOUND` | 400 | El `idRol` enviado no existe |
| `MALFORMED_REQUEST` | 400 | Cuerpo de la peticion que no es JSON valido o con tipos incorrectos |
| `INVALID_PARAMETER` | 400 | Parametro de ruta o de consulta con un tipo invalido (por ejemplo `/usuarios/abc`) |
| `DUPLICATE_EMAIL` | 409 | Ya existe un usuario registrado con ese email |

---

## Roles disponibles

| ID | Nombre | Descripcion |
|---|---|---|
| 1 | `ADMINISTRADOR_RIESGO` | Acceso completo. Puede crear usuarios, listar, activar/desactivar y resetear contrasenas |
| 2 | `ANALISTA_CREDITO` | Acceso limitado. Puede ver su propio perfil, cambiar su contrasena, y usar refresh/validate |

---

## Primer administrador

No existe endpoint publico ni usuario semilla con credencial conocida. El primer `ADMINISTRADOR_RIESGO` se crea al arrancar el backend con las variables de entorno `INIT_ADMIN_EMAIL` e `INIT_ADMIN_PASSWORD` (minimo 12 caracteres), solo si todavia no existe ningun administrador. Detalle en el README ("Primer administrador").

---

## Comportamiento con usuarios desactivados

Cuando un ADMINISTRADOR_RIESGO desactiva un usuario (`PATCH /usuarios/{id}/estado` con `activo: false`):

- **Tokens existentes quedan invalidos inmediatamente.** El filtro JWT verifica el estado `activo` en BD en cada peticion.
- **Login rechazado.** `POST /auth/login` retorna 401 con `INVALID_CREDENTIALS`.
- **Refresh rechazado.** `POST /auth/refresh` retorna 401 y la sesion se revoca.
- **Validate rechazado.** `GET /auth/validate` retorna 401.

El frontend debe manejar un 401 inesperado como sesion terminada y redirigir al login.

---

## Ejemplo de integracion con Axios

```javascript
import axios from 'axios';

const api = axios.create({
  baseURL: 'http://localhost:8081/api/v1',
});

// Interceptor: agregar token a cada request
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Interceptor: manejar 401 (redirigir a login)
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('token');
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

// Login
async function login(email, password) {
  const { data } = await api.post('/auth/login', { email, password });
  localStorage.setItem('token', data.token);
  localStorage.setItem('rol', data.rol);
  return data;
}

// Crear usuario (solo ADMINISTRADOR_RIESGO)
async function createUser(nombre, email, password, idRol) {
  const { data } = await api.post('/usuarios', { nombre, email, password, idRol });
  return data;
}

// Listar usuarios (solo ADMINISTRADOR_RIESGO)
async function listUsers() {
  const { data } = await api.get('/usuarios');
  return data;
}

// Cambiar contrasena propia (ANALISTA_CREDITO)
async function changeMyPassword(userId, currentPassword, newPassword) {
  await api.patch(`/usuarios/${userId}/password`, { currentPassword, newPassword });
}

// Renovar sesion: reemplazar AMBOS tokens; el refresh token anterior ya no sirve
async function renewSession() {
  const { data } = await api.post('/auth/refresh', {
    refreshToken: sessionStorage.getItem('refreshToken'),
  });
  sessionStorage.setItem('token', data.token);
  sessionStorage.setItem('refreshToken', data.refreshToken);
  return data;
}

// Cerrar sesion
async function logout() {
  await api.post('/auth/logout', { refreshToken: sessionStorage.getItem('refreshToken') });
  sessionStorage.removeItem('token');
  sessionStorage.removeItem('refreshToken');
}
```

---

## CORS

El backend acepta peticiones de los origenes configurados en la variable `CORS_ALLOWED_ORIGINS` (default: `http://localhost:3000`). Si el frontend corre en otro puerto u origen, el backend debe configurarse con ese origen.

Multiples origenes se separan por coma:

```
CORS_ALLOWED_ORIGINS=http://localhost:3000,http://localhost:5173
```
