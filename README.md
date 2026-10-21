# Transaction Execution API

API REST que recibe transacciones de crédito/débito, las ejecuta contra un proveedor externo, persiste el
resultado y expone consultas con filtros y paginación. Reto técnico de backend de Spin.

**Stack:** Java 17 · Spring Boot 3.3 · Spring Security · PostgreSQL (Flyway) · Resilience4j · JUnit 5 / Mockito / Testcontainers

**Documentación extendida:** [Arquitectura](docs/ARQUITECTURA.md) · [Seguridad](docs/SEGURIDAD.md) · [Operación, escalado y backups](docs/OPERACION.md)

---

## Núcleo vs. opcional (leer primero)

Para evaluar el reto **no hace falta Kubernetes, nube ni gastar nada**. El núcleo cumple el enunciado completo:

```bash
make run        # API local (H2 + proveedor mock embebido)   |   make up   # con PostgreSQL real (Docker)
make demo       # recorrido guiado de 8 escenarios            |   make test # tests
```
Todo lo demás (backups, Kubernetes local con autoescalado, NetworkPolicy, CI) **existe pero solo corre si se pide**:
`make help` lista cada comando y [`docs/OPCIONALES.md`](docs/OPCIONALES.md) explica costo, requisitos y cómo
activarlo o apagarlo. Material para la presentación y colección de Postman: [`presentacion/`](presentacion/) (importar desde [`presentacion/postman/`](presentacion/postman/LEEME.md)).

---

## Cómo ejecutarlo

Requisitos: JDK 17+ y Maven 3.9+ (o Docker).

### Opción 1 — Sin dependencias externas (H2 en memoria + proveedor mock embebido)
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

### Opción 2 — Docker Compose (PostgreSQL real + proveedor mock embebido)
```bash
docker compose up --build
```

La API queda en `http://localhost:8080`. Swagger UI: `http://localhost:8080/swagger-ui.html`.
Health (liveness/readiness): `/actuator/health`.

### Tests
```bash
mvn verify
```
Incluye tests unitarios (reglas, servicio, adaptador HTTP, resiliencia), integración con H2 y un test contra
**PostgreSQL real** con Testcontainers (migración, mapeo y concurrencia de idempotencia). Este último se omite
automáticamente si Docker no está disponible.

### Autenticación
Todas las rutas requieren el header `X-API-Key` (excepto `/actuator/health/**` y la documentación de la API).
En el perfil `local` la key es `local-dev-key`; en docker compose es `dev-key-change-me` (sobrescribible con
`SECURITY_API_KEYS`). La aplicación **no arranca** si no hay ninguna key configurada.

### Ejemplos
```bash
# Crédito (el proveedor mock arranca cada cuenta en 4000.00, responde balance 5500.00)
curl -i -X POST localhost:8080/transactions -H 'Content-Type: application/json' \
  -H 'X-API-Key: local-dev-key' -H 'Idempotency-Key: 7d1f0c2e-0001' \
  -d '{"accountId":"acc-123456","type":"CREDIT","amount":1500.00,"currency":"MXN","description":"Transferencia recibida"}'

# Consulta con filtros y paginación
curl -H 'X-API-Key: local-dev-key' \
  'localhost:8080/transactions?accountId=acc-123456&type=CREDIT&status=EXECUTED&page=1&limit=20'

# Violación de regla de negocio -> 422 (no se llama al proveedor)
curl -i -X POST localhost:8080/transactions -H 'Content-Type: application/json' -H 'X-API-Key: local-dev-key' \
  -d '{"accountId":"acc-123456","type":"DEBIT","amount":10000.01,"currency":"MXN"}'
```

### Escenarios del proveedor mock
Se activa con `provider.mock.enabled=true` (ya activo en el perfil `local` y en docker compose). El escenario se
elige por el sufijo del `accountId`:

| accountId termina en | Comportamiento |
|---|---|
| *(cualquier otro)* | Aprueba; mantiene saldo en memoria (inicia en 4000.00) |
| `-nofunds` | 422 `REJECTED` / `INSUFFICIENT_FUNDS` |
| `-blocked` | 403 `REJECTED` / `ACCOUNT_BLOCKED` |
| `-down` | 503 siempre (termina en `FAILED`, abre el circuit breaker tras varias llamadas) |
| `-flaky` | 503 las dos primeras llamadas y luego OK (se ve el reintento funcionando) |
| `-slow` | Responde a los 5 s (el read-timeout es 2 s) |

---

## API

### `POST /transactions`
Header opcional `Idempotency-Key`. Body según el enunciado.

| Resultado | HTTP | Notas |
|---|---|---|
| Ejecutada | `201` | `status: EXECUTED`, `providerTransactionId`, `balanceAfter` |
| Rechazada por el proveedor | `201` | `status: REJECTED`, `errorCode`, `errorMessage` (la transacción se registró) |
| Proveedor no disponible | `502` | `status: FAILED`; el resultado en el proveedor es **desconocido** |
| Repetición con misma `Idempotency-Key` | `200` | Devuelve la transacción original sin volver a ejecutar |
| Misma key con payload distinto | `409` | `IDEMPOTENCY_KEY_CONFLICT` |
| Regla de negocio violada | `422` | `AMOUNT_TOO_LOW`, `DEBIT_LIMIT_EXCEEDED`, `UNSUPPORTED_CURRENCY`, `INVALID_AMOUNT_PRECISION` |
| Request inválido | `400` | `VALIDATION_ERROR` con detalle por campo |
| Sin API key o inválida | `401` | `UNAUTHORIZED` |

Todos los errores son `application/problem+json` (RFC 7807) con un campo `code` estable.

### `GET /transactions`
Filtros opcionales `accountId`, `status`, `type`; paginación `page` (desde 1, default 1) y `limit` (default 20, máx. 100).
Orden: más recientes primero. Respuesta: `{ data, page, limit, totalElements, totalPages }`.

### `GET /transactions/{id}`
Consulta puntual.

---

## Arquitectura

```
api/                      Controller, DTOs, manejo de errores (HTTP)
application/              TransactionService (casos de uso, sin dependencias de Spring)
domain/                   Transaction, reglas de negocio, puertos (TransactionRepository, ProviderGateway)
infrastructure/
  persistence/            Adaptador JPA del puerto de repositorio
  provider/               Adaptador HTTP + decorador de resiliencia + mock del proveedor
  security/               Autenticación por API key (deny-by-default)
  config/                 Cableado de beans y OpenAPI
deploy/k8s/               Deployment, HPA, PDB, NetworkPolicy, CronJob de backup
scripts/                  backup.sh / restore.sh (pg_dump / pg_restore verificados)
docs/                     Arquitectura, seguridad, operación
```

Las dependencias apuntan hacia adentro: `domain` y `application` no conocen Spring, JPA ni HTTP. El proveedor y la
persistencia son puertos (interfaces) con adaptadores intercambiables, lo que permite probar el servicio con fakes
sin levantar nada.

## Decisiones de diseño

**Flujo: guardar `PENDING` antes de llamar al proveedor.** El enunciado describe validar → ejecutar → persistir.
En un sistema financiero, si el proceso muere después de que el proveedor movió dinero y antes de guardar, la
transacción se pierde sin rastro. Por eso se persiste `PENDING` primero y se actualiza al estado final
(`EXECUTED` / `REJECTED` / `FAILED`). Una transacción atascada en `PENDING` es detectable y reconciliable.

**Sin `@Transactional` alrededor de la llamada HTTP.** Cada escritura es su propia transacción corta. Así no se
mantiene una conexión del pool ocupada mientras se espera al proveedor, que es lo que primero se rompe con
millones de operaciones diarias.

**Idempotencia.** `Idempotency-Key` con restricción `UNIQUE` en BD: reintentos del cliente no duplican la
operación; si dos peticiones con la misma key compiten, una gana por la restricción y la otra recibe la
transacción ganadora. Además se envía el `id` de la transacción al proveedor como `Idempotency-Key`, de modo que
los reintentos internos tampoco puedan ejecutarse dos veces si el proveedor soporta el header.

**Resiliencia (Resilience4j, componiendo `Retry(CircuitBreaker(HTTP))`).**
- Timeouts: connect 1 s, read 2 s.
- Reintentos (3 intentos, backoff exponencial con jitter) **solo** ante indisponibilidad (red, timeout, 5xx).
  Un `REJECTED` del proveedor es una respuesta definitiva y nunca se reintenta.
- Circuit breaker: con ≥10 llamadas y ≥50 % de fallas abre 10 s y falla rápido (sin reintentar contra un circuito abierto).
- Un timeout es un estado ambiguo (el proveedor pudo haber procesado). Se registra como `FAILED` con
  `PROVIDER_UNAVAILABLE`; la conciliación contra el proveedor usando el id de transacción queda como siguiente paso.

**Persistencia: PostgreSQL.** Datos relacionales y transaccionales, restricciones (`UNIQUE`) para idempotencia,
`NUMERIC(19,2)` para dinero (nunca `double`; en Java `BigDecimal`), índices para las consultas reales
(`account_id, created_at DESC` y `status, created_at DESC`) y camino claro de escalado (réplicas de lectura,
particionado por fecha). Esquema versionado con Flyway; Hibernate solo valida (`ddl-auto=validate`). H2 en modo
PostgreSQL se usa únicamente para el perfil `local` y los tests.

**Validación en dos niveles.** Sintáctica en el DTO (Bean Validation → 400). Reglas de negocio en el dominio
(`TransactionRules` → 422), ejecutadas antes de tocar BD o proveedor: una transacción que viola reglas no se
persiste ni se ejecuta.

**Dinero.** `BigDecimal` con escala 2; se rechazan más de 2 decimales. Monto mínimo *mayor* a 1.00 (1.00 se rechaza);
débito máximo 10,000.00 inclusive; solo `MXN` (se normaliza a mayúsculas).

**Paginación.** Offset/limit con tope de 100 por página. Es lo que pide el enunciado y es simple; con tablas de
cientos de millones de filas se migraría a paginación por cursor (keyset sobre `created_at, id`).

## Seguridad, crecimiento y backups (resumen)
- **Seguridad:** API key en tiempo constante con rotación y arranque fail-safe, deny-by-default, validación estricta,
  errores sin fugas, contenedor no root, NetworkPolicy, Dependabot. Detalle y lo que corresponde al gateway en
  [`docs/SEGURIDAD.md`](docs/SEGURIDAD.md).
- **Crecimiento transparente:** servicio sin estado + HPA (3–20 réplicas) + PDB; la idempotencia la arbitra la BD, así
  que escalar réplicas no cambia la corrección. Dimensionamiento con números y plan para PgBouncer, réplicas de lectura,
  particionado y paginación por cursor en [`docs/OPERACION.md`](docs/OPERACION.md).
- **Backups:** estrategia en 3 capas (HA, PITR con WAL, dump lógico verificado), scripts y CronJob incluidos, y
  procedimiento de restauración.

## Qué haría después (fuera de alcance deliberadamente)
- Job de conciliación para `PENDING` antiguos y `FAILED` (consultar al proveedor por id de transacción).
- Patrón *outbox* + Kafka para publicar eventos `transaction.executed/rejected/failed`.
- Paginación por cursor y particionado de la tabla por fecha.
- Métricas (Micrometer/Prometheus): latencia del proveedor, estado del circuit breaker, tasa de `FAILED`.
- Autenticación fuerte (OAuth2/mTLS) y rate limiting por cliente en el API gateway; columna `client_id` para auditoría.

## Uso de IA
Usé Claude como asistente de ingeniería para discutir el diseño, generar código inicial, pruebas, manifiestos de
despliegue y documentación, y contrasté su propuesta con un plan alternativo de otra IA, adoptando y descartando partes
con criterio propio (p. ej. separar *rechazo* de *caída* del proveedor, y guardar `PENDING` antes de ejecutar).
Compilé y probé el código, ejecuté la suite y la demo, y puedo explicar cada decisión.

- Tiempo real invertido: TODO-RICK horas (detalle por fase en [`docs/USO-DE-IA.md`](docs/USO-DE-IA.md)).
- Lo que verifiqué yo personalmente: TODO-RICK (resumen de una línea).
