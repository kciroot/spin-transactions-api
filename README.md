# Transaction Execution API

API REST que recibe transacciones de crédito/débito, las ejecuta contra un proveedor externo, persiste el
resultado y expone consultas con filtros y paginación. Reto técnico de backend de Spin.

**Stack:** Java 17 · Spring Boot 3.3 · PostgreSQL (Flyway) · Resilience4j · JUnit 5 / Mockito

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
mvn test
```

### Ejemplos
```bash
# Crédito (el proveedor mock arranca cada cuenta en 4000.00, responde balance 5500.00)
curl -i -X POST localhost:8080/transactions -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: 7d1f0c2e-0001' \
  -d '{"accountId":"acc-123456","type":"CREDIT","amount":1500.00,"currency":"MXN","description":"Transferencia recibida"}'

# Consulta con filtros y paginación
curl 'localhost:8080/transactions?accountId=acc-123456&type=CREDIT&status=EXECUTED&page=1&limit=20'

# Violación de regla de negocio -> 422 (no se llama al proveedor)
curl -i -X POST localhost:8080/transactions -H 'Content-Type: application/json' \
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
  config/                 Cableado de beans
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

## Qué haría después (fuera de alcance deliberadamente)
- Job de conciliación para `PENDING` antiguos y `FAILED` (consultar al proveedor por id de transacción).
- Patrón *outbox* + Kafka para publicar eventos `transaction.executed/rejected/failed`.
- Paginación por cursor y particionado de la tabla por fecha.
- Métricas (Micrometer/Prometheus): latencia del proveedor, estado del circuit breaker, tasa de `FAILED`.
- Autenticación/autorización y rate limiting por cuenta en el borde (API gateway).

## Uso de IA
Usé Claude como asistente de ingeniería: para discutir el diseño (flujo `PENDING`, idempotencia, política de
resiliencia), generar el boilerplate inicial (estructura del proyecto, DTOs, configuración, README) y proponer
casos de prueba. Revisé el código y las decisiones; puedo explicar y defender cada parte del diseño y del código.
