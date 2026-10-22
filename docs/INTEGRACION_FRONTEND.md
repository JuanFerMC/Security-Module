# Guia de integracion para el frontend

Para quien desarrolle el frontend (`credit-compass`) y necesite conectarse a este backend de autenticacion. No requiere leer el codigo del backend. El contrato detallado de cada endpoint esta en [`API_REFERENCE.md`](API_REFERENCE.md); esta guia explica **como usarlo**.

Estado verificado contra la version `main` tras HU08, HU09 y la correccion de errores HTTP (ver `REVALIDACION_HU08_HU09.md`).

---

## 1. Datos de conexion

| Dato | Valor |
|---|---|
| URL base (local) | `http://localhost:8081/api/v1` |
| Swagger (para explorar y probar) | `http://localhost:8081/swagger-ui.html` |
| Origen permitido por CORS (por defecto) | `http://localhost:3000` (puerto del frontend) |
| Formato | JSON, `Content-Type: application/json`, UTF-8 |

Sugerencia de variable en el frontend: `VITE_AUTH_BASE_URL=http://localhost:8081`. Es **distinta** de `VITE_API_BASE_URL` (Scoring API, `:8080`) y `VITE_QUERY_BASE_URL` (Query API, `:8082`): hacen falta clientes HTTP separados por backend.

**CORS:** los origenes se configuran en el backend con `CORS_ALLOWED_ORIGINS` (lista separada por comas). Si el frontend corre en otra URL (por ejemplo `http://localhost:5173` o un dominio de pruebas), hay que agregarla alli. Se permiten todos los encabezados, incluido `Authorization`.

---

## 2. Como obtener un usuario para probar

No existe registro publico. El primer administrador se crea al arrancar el backend con variables de entorno:

```powershell
$env:INIT_ADMIN_EMAIL = "admin@empresa.com"
$env:INIT_ADMIN_PASSWORD = "una-clave-de-12-o-mas"   # minimo 12 caracteres
```

Con ese administrador se inicia sesion y se crean los demas usuarios (`POST /usuarios`). Detalle en el README ("Primer administrador").

---

## 3. Flujo de sesion

```
login ──► { token (15 min), refreshToken (7 dias, un solo uso) }
  │
  ├─ cada peticion: Authorization: Bearer <token>
  │
  ├─ token vencido (401) ──► POST /auth/refresh { refreshToken }
  │                           └─► { token nuevo, refreshToken nuevo }  (el anterior ya no sirve)
  │
  └─ cerrar sesion ──► POST /auth/logout { refreshToken }
```

Reglas que el frontend **debe** respetar:

1. **Reemplazar los dos tokens** en cada renovacion. El refresh token anterior queda invalido.
2. **Un solo refresh a la vez.** Si dos peticiones reciben 401 al mismo tiempo, deben compartir una unica renovacion (ver el ejemplo de la seccion 6). Usar dos veces el mismo refresh token se interpreta como robo y **revoca toda la sesion**: el usuario tendra que iniciar sesion de nuevo.
3. **No reintentar un refresh que fallo con 401.** Significa sesion terminada: limpiar tokens y llevar al login.
4. **Al cerrar sesion** llamar a `POST /auth/logout` y descartar ambos tokens.

---

## 4. Endpoints

Todos bajo `/api/v1`. "Publico" = sin `Authorization`.

### 4.1 Autenticacion

| Metodo | Ruta | Acceso | Cuerpo | Respuesta |
|---|---|---|---|---|
| `POST` | `/auth/login` | Publico | `{ "email", "password" }` | 200 `LoginResponse` |
| `POST` | `/auth/refresh` | Publico (con refresh token) | `{ "refreshToken" }` | 200 `LoginResponse` |
| `POST` | `/auth/logout` | Publico (con refresh token) | `{ "refreshToken" }` | 204 sin cuerpo (idempotente) |
| `GET` | `/auth/validate` | Token valido | — | 200 `{ userId, email, rol }` |

`LoginResponse`:

```json
{
  "token": "eyJhbGciOi...",
  "refreshToken": "q3k2Vf0o1xk3wH...",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "rol": "ADMINISTRADOR_RIESGO"
}
```

- `expiresIn` son **segundos** de vigencia del `token` (900 = 15 min).
- El login **no devuelve el id del usuario**. Para obtenerlo (y el correo) llamar a `GET /auth/validate` justo despues del login.
- El correo no distingue mayusculas: `Ana@X.com` y `ana@x.com` son el mismo usuario.

### 4.2 Usuarios

| Metodo | Ruta | Quien puede | Cuerpo |
|---|---|---|---|
| `POST` | `/usuarios` | `ADMINISTRADOR_RIESGO` | `{ nombre, email, password, idRol }` |
| `GET` | `/usuarios` | `ADMINISTRADOR_RIESGO` | — |
| `GET` | `/usuarios/{id}` | Administrador (cualquiera); analista (solo el suyo) | — |
| `PATCH` | `/usuarios/{id}/estado` | `ADMINISTRADOR_RIESGO` | `{ "activo": true \| false }` |
| `PATCH` | `/usuarios/{id}/password` | Administrador (resetea a cualquiera); analista (solo la suya) | `{ "currentPassword"?, "newPassword" }` |

`Usuario` (respuesta de crear, listar y consultar):

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

Notas:

- **`idRol` es numerico** y la API no tiene un endpoint para listar roles. En una base creada por las migraciones, `1` = `ADMINISTRADOR_RIESGO` y `2` = `ANALISTA_CREDITO`. Hoy el frontend debe fijar ese mapa; es una limitacion conocida (ver seccion 9).
- `currentPassword` solo es obligatorio cuando un analista cambia la suya. El administrador no lo envia.
- `PATCH .../password` responde 204.
- Crear usuario responde 201.
- Nunca se devuelven contrasenas ni hashes.

### 4.3 Roles

| Rol (`rol` / `rolNombre`) | Que puede hacer |
|---|---|
| `ADMINISTRADOR_RIESGO` | Todo: crear, listar y activar/desactivar usuarios, resetear contrasenas |
| `ANALISTA_CREDITO` | Ver su propio perfil y cambiar su propia contrasena |

El backend es quien hace cumplir los permisos (devuelve 403). El frontend solo debe **ocultar** lo que el rol no puede usar.

---

## 5. Reglas de validacion (para validar en el formulario)

| Campo | Regla |
|---|---|
| `email` | Obligatorio, formato de correo, maximo 255. Sin espacios. |
| `password` / `newPassword` | **12 a 72 caracteres** |
| `nombre` | Obligatorio, maximo 150 |
| `idRol` | Obligatorio, numero |

Ejemplo con `zod`:

```ts
import { z } from "zod";

export const loginSchema = z.object({
  email: z.string().trim().email("Ingresa un correo valido"),
  password: z.string().min(1, "Ingresa tu contrasena"), // en login no se valida longitud
});

export const passwordSchema = z
  .string()
  .min(12, "Usa al menos 12 caracteres")
  .max(72, "Usa como maximo 72 caracteres");

export const createUserSchema = z.object({
  nombre: z.string().trim().min(1).max(150),
  email: z.string().trim().email().max(255),
  password: passwordSchema,
  idRol: z.number().int(),
});
```

> Importante: en **login** no se debe exigir 12 caracteres (usuarios con claves antiguas podrian no poder entrar); la politica se valida solo al crear usuario o cambiar contrasena.

---

## 6. Ejemplo de cliente (TypeScript)

Cliente con adjuntado del token, un unico refresh compartido y un reintento. Adaptar a `createHttpClient` de `src/api/http.ts` (ya tiene los ganchos `getToken` y `onUnauthorized`).

```ts
const AUTH_URL = import.meta.env.VITE_AUTH_BASE_URL ?? "http://localhost:8081";

type Session = { token: string; refreshToken: string; rol: string };

let session: Session | null = null;      // access token: solo en memoria
let refreshing: Promise<boolean> | null = null;

const KEY = "refreshToken";              // refresh token: sessionStorage (se pierde al cerrar la pestana)

function save(r: { token: string; refreshToken: string; rol: string }) {
  session = { token: r.token, refreshToken: r.refreshToken, rol: r.rol };
  sessionStorage.setItem(KEY, r.refreshToken);
}

function clear() {
  session = null;
  sessionStorage.removeItem(KEY);
}

async function raw(path: string, init: RequestInit = {}) {
  return fetch(`${AUTH_URL}/api/v1${path}`, {
    ...init,
    headers: {
      "Content-Type": "application/json",
      ...(session ? { Authorization: `Bearer ${session.token}` } : {}),
      ...init.headers,
    },
  });
}

export async function login(email: string, password: string) {
  const res = await raw("/auth/login", { method: "POST", body: JSON.stringify({ email, password }) });
  if (!res.ok) throw await res.json();   // ApiError
  save(await res.json());
}

// Una sola renovacion en vuelo: todas las peticiones con 401 esperan la misma promesa.
function refresh(): Promise<boolean> {
  refreshing ??= (async () => {
    const refreshToken = session?.refreshToken ?? sessionStorage.getItem(KEY);
    if (!refreshToken) return false;
    const res = await raw("/auth/refresh", { method: "POST", body: JSON.stringify({ refreshToken }) });
    if (!res.ok) { clear(); return false; }   // 401: sesion terminada
    save(await res.json());                   // reemplaza AMBOS tokens
    return true;
  })().finally(() => { refreshing = null; });
  return refreshing;
}

export async function authFetch(path: string, init?: RequestInit) {
  let res = await raw(path, init);
  if (res.status === 401 && (await refresh())) res = await raw(path, init); // un solo reintento
  return res;
}

export async function logout() {
  const refreshToken = session?.refreshToken ?? sessionStorage.getItem(KEY);
  if (refreshToken) {
    await raw("/auth/logout", { method: "POST", body: JSON.stringify({ refreshToken }) }).catch(() => {});
  }
  clear();
}
```

Al recargar la pagina el token en memoria se pierde: si hay `refreshToken` en `sessionStorage`, llamar a `refresh()` al iniciar la aplicacion para restaurar la sesion; si falla, mostrar el login.

---

## 7. Manejo de errores

Toda respuesta de error tiene el mismo formato:

```json
{
  "errorCode": "VALIDATION_ERROR",
  "message": "Error de validacion en los datos de entrada",
  "details": { "password": "size must be between 12 and 72" },
  "traceId": "550e8400-e29b-41d4-a716-446655440000",
  "timestamp": "2026-10-14T14:30:00"
}
```

`details` solo aparece en `VALIDATION_ERROR` y es un objeto `{ campo: mensaje }`. `traceId` sirve para pedir soporte.

| HTTP | `errorCode` | Cuando | Que hacer en la UI |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Campos invalidos | Mostrar `details` junto a cada campo |
| 400 | `ROL_NOT_FOUND` | `idRol` no existe | Error de programacion: revisar el mapa de roles |
| 400 | `ROL_NOT_ALLOWED` | Rol fuera de los dos permitidos | Idem |
| 400 | `MALFORMED_REQUEST` / `INVALID_PARAMETER` | JSON o parametro invalido | Error de programacion |
| 401 | `INVALID_CREDENTIALS` (login) | Correo o contrasena incorrectos, o usuario desactivado | Mensaje generico "Correo o contrasena incorrectos". **El backend no indica cual fallo** |
| 401 | `INVALID_CREDENTIALS` (refresh) | Refresh token vencido, usado, revocado o invalido | Limpiar sesion y llevar al login |
| 401 | `UNAUTHORIZED` | Falta el token o el access token vencio | Intentar `refresh()` una vez; si falla, login |
| 403 | `ACCESS_DENIED` | El rol no tiene permiso | Mostrar "No tienes permisos"; **no** cerrar sesion |
| 404 | `USER_NOT_FOUND` | El usuario no existe | Mensaje de no encontrado |
| 409 | `DUPLICATE_EMAIL` | El correo ya esta registrado | Mostrar el error en el campo correo |

Recomendaciones:

- Distinguir **401 en una peticion normal** (intentar refresh) de **401 en `/auth/refresh`** (sesion terminada). No encadenar refresh sobre refresh.
- Un **403 no es sesion vencida**: el usuario sigue autenticado.

---

## 8. Seguridad en el cliente

- **Access token en memoria**, no en `localStorage`.
- **Refresh token en `sessionStorage`** (pierde el valor al cerrar la pestana). Evitar `localStorage`: ademas de ser legible por scripts, con varias pestanas se reutilizaria el mismo refresh token y la sesion se revocaria.
- La API no usa cookies `HttpOnly`; por eso el refresh token es accesible a JavaScript. Mitigaciones: no incluir scripts de terceros no confiables, sanear todo HTML y mantener dependencias al dia.
- No registrar tokens en consola ni en herramientas de monitoreo.
- Tras desactivar a un usuario, su sesion deja de renovarse; su access token actual puede seguir sirviendo hasta 15 minutos como maximo.

---

## 9. Limitaciones conocidas (para planificar)

| Limitacion | Efecto en el frontend |
|---|---|
| No hay endpoint para listar roles | El mapa `idRol` ↔ rol se fija en el cliente (1 y 2 en una base nueva) |
| El login no devuelve `userId` | Llamar a `GET /auth/validate` tras el login |
| No hay endpoint de "mi perfil" | Usar `GET /usuarios/{id}` con el `userId` obtenido |
| No hay registro publico | La pantalla `/crear-cuenta` actual debe retirarse; el alta es del administrador |
| Cambiar la contrasena no cierra otras sesiones | Las sesiones abiertas siguen hasta 7 dias |
| Sin limite de intentos de login | El frontend no debe asumir proteccion contra fuerza bruta |

Si alguna de las tres primeras bloquea el diseno de pantallas, se puede agregar al backend (por ejemplo `GET /roles` y `userId` en el login) en una HU aparte.

---

## 10. Lista de cambios sugerida en el frontend

Segun el estado actual de `credit-compass` (login simulado en `src/api/auth.ts`):

- [ ] Agregar `VITE_AUTH_BASE_URL` a `.env.example`, `compose.yaml` y `DEPLOYMENT.md`.
- [ ] Crear el segundo cliente HTTP (`src/api/http.ts` ya admite `getToken` y `onUnauthorized`).
- [ ] Reemplazar `login()` / `logout()` simulados por las llamadas reales y guardar ambos tokens (`token-store.ts`).
- [ ] Implementar la renovacion con una sola promesa compartida (seccion 6).
- [ ] Obtener `userId` con `GET /auth/validate` tras el login.
- [ ] Subir la validacion de contrasena a 12 caracteres (solo al crear o cambiar).
- [ ] Retirar o redirigir `/crear-cuenta`; crear el modulo de usuarios para `ADMINISTRADOR_RIESGO`.
- [ ] Cambiar los nombres de rol a `ADMINISTRADOR_RIESGO` y `ANALISTA_CREDITO`.
- [ ] Mapear los `errorCode` de la seccion 7 a mensajes de la UI.
- [ ] Mantener `VITE_SCORING_SENDS_TOKEN=false` hasta que Scoring API permita el encabezado `Authorization` en su CORS.

---

## 11. Probar sin el frontend

```bash
# Login
curl -s -X POST http://localhost:8081/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@empresa.com","password":"una-clave-de-12-o-mas"}'

# Renovar (usar el refreshToken recibido)
curl -s -X POST http://localhost:8081/api/v1/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"<refreshToken>"}'

# Listar usuarios (usar el token recibido)
curl -s http://localhost:8081/api/v1/usuarios -H "Authorization: Bearer <token>"
```

En Windows PowerShell, `curl` es un alias de `Invoke-WebRequest`; usar `curl.exe` o los comandos de Swagger (`/swagger-ui.html`, boton **Authorize** con el `token`).
