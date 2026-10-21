# Demo en Postman

## Importar
1. Postman → **Import** → arrastra los 4 archivos de esta carpeta:
   - `Spin-Transactions-API.postman_collection.json` (la colección)
   - un `env-*.postman_environment.json` según cómo levantes el proyecto (puedes importar los tres)
2. Arriba a la derecha selecciona el **environment**:

| Cómo levantaste | Environment | API key |
|---|---|---|
| `make run` | Spin - Local | `local-dev-key` |
| `make up` | Spin - Docker Compose | `dev-key-change-me` |
| `make k8s-local` + `kubectl -n transactions port-forward svc/transactions-api 8080:80` | Spin - Kubernetes local | `local-k8s-key` |

   Si usaste una key propia (`SECURITY_API_KEYS=...`), cámbiala en el environment (variable `apiKey`).

## Ejecutar
- **Request por request** para explicarlo en vivo, o **Collection Runner** (clic derecho en la colección → *Run*)
  para correr todo en orden. Cada request trae tests: verde = comportamiento esperado.
- Orden recomendado para presentar: carpetas **1 → 2 → 3 → 5 → 6**. La 0 es para verificar que la app está viva;
  la 4 y la 7 son de apoyo.
- Cada sesión usa una cuenta nueva (`accountId` se genera sola), así el saldo del mock siempre empieza en 4000.00.
  Para reiniciar la sesión, borra la variable de colección `runId`.

## Qué cubre
| Carpeta | Qué demuestra |
|---|---|
| 0. Salud y documentación | health (liveness/readiness) y OpenAPI, públicos |
| 1. Flujo feliz e idempotencia | 201 EXECUTED con balance 5500.00, replay 200, conflicto 409, consulta por id, 404 |
| 2. Escenarios del proveedor | REJECTED, cuenta bloqueada, reintento (flaky), caída (502 FAILED), timeout |
| 3. Reglas de negocio | 422: mínimo, tope de débito, sin tope de crédito, moneda |
| 4. Validación de formato | 400 con detalle por campo |
| 5. Consultas | filtros `accountId`/`status`/`type`, paginación, errores de paginación |
| 6. Seguridad | 401 sin key y con key incorrecta |
| 7. Proveedor mock directo | el contrato `POST /provider/v1/execute` (solo con el mock activo) |

## Trucos para la demo
- **Mostrar por qué existe el retry:** levanta con `PROVIDER_RETRY_MAX_ATTEMPTS=1 make up` y ejecuta *2.3 flaky*: pasa
  de `201 EXECUTED` a `502 FAILED`.
- **Circuit breaker:** ejecuta *2.4 (-down)* unas 10 veces: las primeras tardan por los reintentos, luego responde
  inmediato porque el circuito está abierto.
- La carpeta 2.5 (`-slow`) tarda varios segundos: úsala solo si quieres mostrar el timeout.
- Swagger UI también sirve: `{{baseUrl}}/swagger-ui.html` → **Authorize** → pega la API key.
