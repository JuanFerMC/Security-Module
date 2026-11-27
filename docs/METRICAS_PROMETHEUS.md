# Métricas Prometheus en auth-api

`GET /actuator/prometheus` publica las métricas de Micrometer (peticiones HTTP, JVM, pool de conexiones,
proceso). Mismo diseño que scoring-api y evaluation-query-api:

- Protegido con HTTP Basic y una **cuenta técnica exclusiva del scraper**, independiente de los JWT. Ni un JWT de
  administrador lee métricas, y la cuenta técnica no da acceso a la API.
- Solo `GET`; los demás métodos se deniegan.
- **Apagado por defecto** (`AUTH_METRICS_ENABLED=false`): la cadena principal deniega `/actuator/prometheus`
  de forma explícita (401 sin token, 403 con JWT de administrador).
- `/actuator/health` sigue público y sin detalles; el resto de Actuator sigue reservado a `ADMINISTRADOR_RIESGO`.
- Si se habilita sin usuario o contraseña, la aplicación no arranca.

## Configuración

| Variable | Valor |
|---|---|
| `AUTH_METRICS_ENABLED` | `true` para habilitar la cadena de métricas |
| `AUTH_METRICS_USERNAME` | Usuario técnico del scraper |
| `AUTH_METRICS_PASSWORD` | Contraseña fuerte y exclusiva del scraper (secreto) |

Las credenciales viven solo en el entorno de ejecución: nunca en el repositorio, en imágenes ni en logs. En el
entorno local integrado (`scoring-api/orquestacion`) ya vienen configuradas junto con Prometheus y Grafana. En
Render, agregar las tres variables como secretos solo si un Prometheus externo leerá las métricas, siempre por HTTPS.

## Verificación manual

```bash
curl -i http://localhost:8081/actuator/prometheus                                      # 401
curl -i -u "$AUTH_METRICS_USERNAME:$AUTH_METRICS_PASSWORD" http://localhost:8081/actuator/prometheus   # 200
```

Pruebas automáticas: `MetricasPrometheusIntegrationTest` (habilitadas) y
`HttpErrorHandlingIntegrationTest#prometheus_withMetricsDisabled_isDeniedEvenToAdminJwt` (apagadas).
