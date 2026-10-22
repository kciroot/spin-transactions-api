# Transaction Execution API

API REST (Java 17 + Spring Boot 3.3) que ejecuta transacciones de **crédito y débito** contra un proveedor externo,
persiste el resultado en PostgreSQL y permite consultarlas con filtros y paginación. Reto técnico de backend de Spin.

**En 30 segundos**
- Recibe `POST /transactions`, valida reglas de negocio, llama al proveedor, guarda y responde con el estado.
- Pensada para alto volumen: sin estado (escala horizontal), **idempotente**, resiliente (timeouts, reintentos,
  circuit breaker) y con negocio aislado de los frameworks (arquitectura hexagonal).
- Diseño guiado por una idea: *en dinero, nunca ejecutar dos veces ni perder una operación.*
- El **núcleo** cumple el enunciado completo y se prueba sin nube ni costo. Todo lo avanzado es **opcional**.

---

## 1. Inicio rápido (núcleo, gratis)

| Quiero… | Comando | Requiere | API key |
|---|---|---|---|
| Probar lo más simple | `make run` | JDK 17+, Maven | `local-dev-key` |
| Probar con PostgreSQL real | `make up` | Docker | `dev-key-change-me` (o `make key` para una propia) |
| Recorrer 8 escenarios guiados | `make demo` | `curl` + app arriba | la del modo elegido |
| Correr los tests | `make test` / `make verify` | JDK, Maven (Docker opcional) | — |

Sin `make`: `mvn spring-boot:run -Dspring-boot.run.profiles=local` (equivale a `make run`) y `docker compose up --build`.

Al arrancar: API en `http://localhost:8080` · Swagger en `/swagger-ui.html` · health en `/actuator/health`.

## 2. Cómo probarlo

- **Terminal:** `make demo` (8 escenarios con los códigos esperados).
- **Postman (demo visual para el equipo):** importar `presentacion/postman/` (colección con 37 requests y tests +
  environments). Instrucciones en [`presentacion/postman/LEEME.md`](presentacion/postman/LEEME.md).
- **Swagger UI:** `/swagger-ui.html` → *Authorize* → pegar la API key.
- **curl:**
```bash
curl -i -X POST localhost:8080/transactions -H 'Content-Type: application/json' \
  -H 'X-API-Key: local-dev-key' -H 'Idempotency-Key: demo-001' \
  -d '{"accountId":"acc-123456","type":"CREDIT","amount":1500.00,"currency":"MXN","description":"Transferencia recibida"}'

curl -H 'X-API-Key: local-dev-key' 'localhost:8080/transactions?accountId=acc-123456&status=EXECUTED&page=1&limit=20'
```
> **API key:** no se "obtiene", se define. En los modos de arriba ya hay una por defecto; para una propia: `make key`
> (guarda una aleatoria en `.env`, ignorado por git) o `SECURITY_API_KEYS=mi-key`. La app no arranca sin ninguna key.

## 3. Núcleo vs. opcional

| Nivel | Qué incluye | Cómo se activa | Costo |
|---|---|---|---|
| **Núcleo** | API completa, reglas, proveedor mock, persistencia, idempotencia, resiliencia, API key, tests, demo, Docker Compose | `make run` / `make up` | $0 |
| Opcional | Backups verificados y restore | `make backup` · `make restore FILE=…` | $0 |
| Opcional | Kubernetes local (kind): núcleo, o con autoescalado + PDB + NetworkPolicy + CronJob | `make k8s-local` · `make k8s-local-full` | $0 |
| Opcional | Prueba de carga (k6) y escaneo de imagen (Trivy) | `make load` · `make scan` | $0 |
| Opcional | CI y Dependabot | automático en GitHub | $0 |
| Opcional (no ejecutado) | Despliegue en nube real | `deploy/k8s/overlays/prod-example` (ejemplo) | variable |

`make help` lista todo. **Ningún opcional corre por defecto.** Detalle y perillas: [`docs/OPCIONALES.md`](docs/OPCIONALES.md).

---

## 4. API

### `POST /transactions`
Headers: `X-API-Key` (obligatorio), `Idempotency-Key` (opcional, recomendado).
```json
{ "accountId": "acc-123456", "type": "CREDIT", "amount": 1500.00, "currency": "MXN", "description": "Transferencia recibida" }
```

| Resultado | HTTP | `status` / `code` |
|---|---|---|
| Aprobada | `201` | `EXECUTED` + `providerTransactionId`, `balanceAfter` |
| Rechazada por el proveedor | `201` | `REJECTED` + `errorCode` (se registra) |
| Proveedor no disponible | `502` | `FAILED` (resultado **desconocido**, requiere conciliación) |
| Misma `Idempotency-Key` | `200` | devuelve la transacción original, sin volver a ejecutar |
| Misma key con otro payload | `409` | `IDEMPOTENCY_KEY_CONFLICT` |
| Regla de negocio violada | `422` | `AMOUNT_TOO_LOW`, `DEBIT_LIMIT_EXCEEDED`, `UNSUPPORTED_CURRENCY` |
| Request inválido | `400` | `VALIDATION_ERROR` (detalle por campo) |
| Sin API key o inválida | `401` | `UNAUTHORIZED` |

**Reglas de negocio** (se aplican *antes* de llamar al proveedor): monto **mayor a 1.00** · `DEBIT` hasta **10,000.00**
(crédito sin límite) · solo **MXN**. Errores en formato RFC 7807 con un campo `code` estable.

### `GET /transactions`
Filtros opcionales `accountId`, `status`, `type`; `page` (desde 1) y `limit` (1–100, por defecto 20). Más recientes
primero. Respuesta: `{ data, page, limit, totalElements, totalPages }`. También `GET /transactions/{id}`.

### Proveedor mock (escenarios por sufijo del `accountId`)
| Termina en | Comportamiento |
|---|---|
| *(otro)* | Aprueba; cada cuenta inicia en 4000.00 (crédito de 1500.00 → balance 5500.00) |
| `-nofunds` / `-blocked` | Rechazo definitivo (`INSUFFICIENT_FUNDS` / `ACCOUNT_BLOCKED`) |
| `-down` | 503 siempre → `FAILED`; tras varias fallas abre el circuit breaker |
| `-flaky` | Falla 2 veces y luego OK → el reintento lo deja `EXECUTED` |
| `-slow` | Responde a los 5 s (timeout de lectura: 2 s) |

---

## 5. Cómo funciona

```
Cliente ─X-API-Key─► security ─► api ─► application ─► domain ◄── persistence ──► PostgreSQL
                                          (flujo)      (reglas,    ◄── provider ───► Proveedor
                                                        puertos)   (HTTP + Retry + CircuitBreaker)
```
Flujo de `POST /transactions`: **1)** autenticación → **2)** validación de formato → **3)** idempotencia → **4)** reglas
de negocio → **5)** guardar `PENDING` → **6)** llamar al proveedor → **7)** guardar `EXECUTED` / `REJECTED` / `FAILED`.

Estados: `PENDING` → `EXECUTED` (aprobada) · `REJECTED` (rechazo definitivo) · `FAILED` (sin respuesta definitiva).

## 6. Decisiones de diseño (el porqué)

| Decisión | Por qué |
|---|---|
| **Hexagonal** (dominio y aplicación sin Spring) | Reglas y flujo se prueban sin framework; la BD y el proveedor son intercambiables |
| **`PENDING` antes del proveedor** | Si el proceso muere tras mover dinero y antes de guardar, la operación no se pierde. Costo: un `UPDATE` extra. Desvío consciente del orden del enunciado |
| **Idempotencia con `UNIQUE` en BD** | Los reintentos del cliente no duplican dinero y funciona con N réplicas (el árbitro no es memoria local) |
| **Rechazo ≠ caída** | Un rechazo es definitivo: no se reintenta ni afecta al circuit breaker. Una caída es ambigua: se reintenta y queda `FAILED` |
| **Sin `@Transactional` en el servicio** | No se retiene una conexión del pool mientras se espera al proveedor (cuello de botella real a gran escala) |
| **`Retry(CircuitBreaker(HTTP))` por decorador** | Orden explícito y testeable; con el circuito abierto falla rápido sin reintentar |
| **PostgreSQL + `NUMERIC(19,2)` + `BigDecimal`** | Transaccional, unicidad atómica y dinero exacto (nunca `double`) |
| **Validación en dos niveles** | Formato en el DTO (400); negocio en el dominio (422), antes de tocar BD o proveedor |
| **API key en el servicio** | Es servicio-a-servicio; OAuth2/mTLS y rate limiting corresponden al gateway |
| **Opcionales aislados** | El núcleo siempre arranca y no depende de nube ni costo |

Razonamiento completo con alternativas descartadas: [`docs/ARQUITECTURA.md`](docs/ARQUITECTURA.md).

## 7. Tecnologías

| Tecnología | Uso | Por qué |
|---|---|---|
| Java 17 · Spring Boot 3.3 | Lenguaje y framework | Stack de Spin; records, `sealed`, ecosistema maduro |
| Spring Data JPA · Hibernate · Flyway | Persistencia y esquema versionado | Filtros parametrizados; Hibernate solo valida, Flyway migra |
| PostgreSQL 16 (H2 en `local`/tests) | Base de datos | Unicidad atómica, `NUMERIC` exacto, escalable |
| Resilience4j (retry + circuit breaker) | Resiliencia ante el proveedor | Ligera, sin AOP, orden explícito |
| Spring Security | API key, deny-by-default | Aislada en `infrastructure/security` |
| springdoc-openapi · Actuator | Swagger y health/readiness | Contrato visible y probes de K8s |
| JUnit 5 · Mockito · MockMvc · Testcontainers | Pruebas | Unitarias, de API y contra PostgreSQL real |
| Docker · Compose · Kustomize · kind | Empaquetado y despliegue opcional | Reproducible y modular |
| GitHub Actions · Dependabot · Make · Postman | CI, dependencias, panel de control y demo | Calidad y facilidad de uso |

Tabla ampliada (qué hace cada una y alternativas): [`docs/PROYECTO-DETALLADO.md` §3](docs/PROYECTO-DETALLADO.md).

## 8. Pruebas

**44 tests**: `TransactionRulesTest` (8) · `TransactionServiceTest` (11) · `HttpProviderGatewayTest` (7) ·
`ResilientProviderGatewayTest` (4) · `TransactionControllerIT` (12, API completa sobre H2) · `PostgresIT` (2, PostgreSQL real
con Testcontainers: migración y **12 hilos con la misma `Idempotency-Key` crean una sola transacción**).

```bash
make verify       # todos los tests (Testcontainers se omite solo si no hay Docker)
make evidencia    # además guarda logs reales de tests y demo en ./evidencias
```
Qué prueba cada clase y cómo: [`docs/PROYECTO-DETALLADO.md` §6](docs/PROYECTO-DETALLADO.md).

## 9. Configuración (variables de entorno)

| Variable | Default | Para qué |
|---|---|---|
| `SECURITY_API_KEYS` | *(obligatoria)* | Una o varias keys separadas por coma (rotación sin downtime) |
| `DB_URL` / `DB_USER` / `DB_PASSWORD` | PostgreSQL local | Base de datos |
| `PROVIDER_BASE_URL` · `PROVIDER_MOCK_ENABLED` | `http://localhost:8080` · `false` | Proveedor real o mock embebido |
| `PROVIDER_RETRY_MAX_ATTEMPTS` | `3` | `1` = sin reintentos |
| `PROVIDER_READ_TIMEOUT` · `PROVIDER_CONNECT_TIMEOUT` | `2s` · `1s` | Timeouts hacia el proveedor |
| `PROVIDER_CB_*` | ver `application.yml` | Umbrales del circuit breaker |
| `SPRINGDOC_ENABLED` | `true` | `false` en producción |
| `DB_POOL_SIZE` | `20` | Tamaño del pool |

## 10. Seguridad, crecimiento y backups (resumen)
- **Seguridad:** API key en tiempo constante con rotación y arranque fail-safe, deny-by-default, validación estricta,
  consultas parametrizadas, errores sin fugas, contenedor no root, NetworkPolicy y Dependabot. → [`docs/SEGURIDAD.md`](docs/SEGURIDAD.md)
- **Crecimiento:** servicio sin estado + autoescalado (opcional); dimensionamiento con números, PgBouncer, réplicas de
  lectura, particionado y paginación por cursor. → [`docs/OPERACION.md`](docs/OPERACION.md)
- **Backups:** tres capas (alta disponibilidad, PITR con WAL, dump lógico verificado) con scripts y CronJob. → [`docs/OPERACION.md`](docs/OPERACION.md)

## 11. Estructura del proyecto
```
src/main/java/com/spin/transactions/
  domain/            Transaction, reglas, puertos, excepciones
  application/       TransactionService (casos de uso)
  api/               Controller, DTOs, manejo de errores
  infrastructure/    persistence · provider (HTTP, resiliencia, mock) · security · config
src/main/resources/  application*.yml, db/migration (Flyway)
src/test/            unitarios, adaptadores, resiliencia, integración (H2 y Testcontainers)
deploy/k8s/          base + components (opt-in) + overlays (local, local-full, prod-example)
scripts/             demo.sh, backup.sh, restore.sh
docs/                documentación detallada
presentacion/        presentación HTML, guía paso a paso, colección Postman
Makefile · Dockerfile · docker-compose.yml · .github/
```

## 12. Documentación
| Documento | Contenido |
|---|---|
| [`docs/COBERTURA-REQUISITOS.md`](docs/COBERTURA-REQUISITOS.md) | **Matriz del reto**: cada punto del PDF → qué lo cubre y dónde; separa lo pedido del plus |
| [`docs/PROYECTO-DETALLADO.md`](docs/PROYECTO-DETALLADO.md) | **Todo por fases**: tecnologías y porqué, qué hace cada componente, comportamiento, pruebas |
| [`docs/ARQUITECTURA.md`](docs/ARQUITECTURA.md) | Diagramas y razonamiento de diseño |
| [`docs/SEGURIDAD.md`](docs/SEGURIDAD.md) | Amenazas y controles |
| [`docs/OPERACION.md`](docs/OPERACION.md) | Capacidad, escalado, backups, runbook |
| [`docs/OPCIONALES.md`](docs/OPCIONALES.md) | Núcleo vs opcional, costos y perillas |
| [`docs/USO-DE-IA.md`](docs/USO-DE-IA.md) | Prompts, decisiones, validaciones y pruebas |
| [`presentacion/`](presentacion/) | Presentación, guía paso a paso y Postman |

## 13. Límites y siguientes pasos
**Carga:** el dimensionamiento a millones de transacciones/día es un modelo por cálculo, **aún no medido**; hay una prueba de
carga opcional (`make load`, k6) para validarlo ([`docs/OPERACION.md` §7](docs/OPERACION.md)).

Diseñado pero no implementado: conciliación de `PENDING`/`FAILED`, métricas y alertas, `client_id` para auditoría,
paginación por cursor y particionado, outbox + Kafka. El mock no respeta `Idempotency-Key` (un proveedor real debería).

Optimizaciones identificadas en la revisión y **no aplicadas a ciegas** (requieren compilar/medir con `make verify` y `make load`):
1. **Reintento seguro de `FAILED` con la misma `Idempotency-Key`**: hoy la repetición devuelve el `FAILED` guardado; lo ideal es
   re-llamar al proveedor con el mismo `transactionId` (idempotente) usando un `UPDATE ... WHERE status='FAILED'` como candado.
2. **Paginación sin `COUNT(*)`**: en páginas llenas se cuenta toda la tabla; a gran volumen conviene `hasNext` (limit+1) o cursor.
3. **`UPDATE` directo en vez de `merge`**: evita un `SELECT` por transacción (3 sentencias → 2).

## 14. Uso de IA
Usé Claude como asistente de ingeniería bajo un modelo de **dirección con revisión**. Definí la priorización (núcleo primero,
plus opcional), impuse requisitos no funcionales (**seguridad, escalamiento, recuperación, control de costo, explicabilidad**),
**contrasté su propuesta con el plan de otra IA** y exigí la justificación de cada componente y tecnología. La IA generó código,
tests, manifiestos y documentación; **yo ejecuto y valido el resultado** (suite de tests, demo y colección de Postman) y guardo
la evidencia en `evidencias/`.

- Requisitos impuestos, bitácora de dirección, decisiones, validación y pruebas: [`docs/USO-DE-IA.md`](docs/USO-DE-IA.md)
- Revisiones adicionales de seguridad, escalabilidad y calidad: [`docs/PROMPTS-DE-REVISION.md`](docs/PROMPTS-DE-REVISION.md)
- Tiempo real invertido: TODO-RICK horas (detalle por fase en el documento anterior).
- Lo que verifiqué personalmente: TODO-RICK (resumen de una línea basado en `evidencias/`).
