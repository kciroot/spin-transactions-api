# Proyecto detallado: fases, tecnologías, componentes, comportamiento y pruebas

Este documento explica **todo** lo construido: en qué orden, con qué tecnologías y por qué, qué hace cada pieza,
cómo se comporta el sistema en cada situación y cómo se prueba. Para el resumen rápido ver el [README](../README.md);
para el razonamiento de diseño ver [ARQUITECTURA](ARQUITECTURA.md).

> **Estado de verificación (honesto):** el dominio y la capa de aplicación (19 tests) se ejecutaron y pasaron.
> El resto (Spring, JPA, seguridad, adaptador HTTP, Resilience4j, tests de integración, Docker, Kubernetes) está
> escrito y revisado pero se confirma con `make verify` / `make evidencia` en la máquina de quien lo entrega.
> El arranque local de la aplicación (Flyway + JPA) ya se observó en un log real.

---

## 1. Resumen ejecutivo

API REST en **Java 17 + Spring Boot 3.3** que recibe transacciones de crédito y débito, valida reglas de negocio,
las ejecuta contra un proveedor externo (mockeado), persiste el resultado en PostgreSQL y expone consultas con filtros y
paginación. Diseñada para alto volumen: sin estado (escala horizontal), idempotente, resiliente ante caídas del
proveedor y con una arquitectura hexagonal donde el negocio no depende de ningún framework.

**Idea central del diseño:** en dinero, lo peor es ejecutar dos veces o perder una operación. De ahí salen las
decisiones principales: guardar `PENDING` antes de llamar al proveedor, idempotencia arbitrada por la base de datos y
separar un rechazo (definitivo) de una caída (ambigua).

---

## 2. Fases del proyecto

| # | Fase | Objetivo | Qué se construyó | Verificación |
|---|---|---|---|---|
| 0 | Análisis del enunciado | Entender requisitos y criterios de evaluación | Lista de requisitos, riesgos (alto volumen, dinero) y alcance en dos niveles: **núcleo** y **plus** | Revisión contra el PDF |
| 0b | Contraste con otro plan | No adoptar una sola propuesta sin comparar | Comparativa con un plan de otra IA: se adoptó la idea de Testcontainers y se descartó mapear toda falla a `REJECTED` | Tabla en `docs/USO-DE-IA.md` |
| 1 | Diseño | Definir arquitectura, estados y flujo | Hexagonal; estados `PENDING/EXECUTED/REJECTED/FAILED`; idempotencia; política de resiliencia | `docs/ARQUITECTURA.md` |
| 2 | Dominio y aplicación | Reglas y flujo sin framework | `Transaction`, `TransactionRules`, puertos, `TransactionService` | **19 tests ejecutados y en verde** |
| 3 | Infraestructura | Persistencia y proveedor | Adaptador JPA + Flyway; cliente HTTP; Retry + CircuitBreaker; mock del proveedor | Tests de adaptador y resiliencia (a confirmar con `make verify`) |
| 4 | API | Contrato HTTP | Controller, DTOs, códigos de estado, errores RFC 7807 | Tests MockMvc |
| 5 | Seguridad | Proteger la API | API key en tiempo constante, deny-by-default, fail-safe, contenedor endurecido | Tests 401 + revisión |
| 6 | Pruebas de integración | Probar el conjunto y la concurrencia | H2 para la suite rápida; Testcontainers contra PostgreSQL real | `TransactionControllerIT`, `PostgresIT` |
| 7 | Empaquetado y operación | Ejecutar y desplegar | Dockerfile multi-etapa, Docker Compose, Kustomize, backups, CI | `docker compose up`, `make k8s-local` (opcionales) |
| 8 | Control de opcionales | Que nada avanzado estorbe el núcleo | `Makefile`, perfil de compose, componentes Kustomize opt-in, perillas por variable de entorno | `make help`, `docs/OPCIONALES.md` |
| 9 | Documentación y presentación | Poder explicarlo y defenderlo | Docs, presentación HTML, guía paso a paso, colección Postman, guía de estudio | Revisión |
| 10 | Soporte y depuración | Resolver problemas de uso reales | Diagnóstico del 401 por API key; `make key`; log de keys cargadas | Log de arranque real |

**Por qué este orden:** primero lo que concentra el riesgo de negocio (dominio) y se puede probar aislado; después
lo que depende de él (infraestructura, API, seguridad); al final lo operativo y lo documental. Cada capa se apoya en la
anterior ya verificada.

---

## 3. Tecnologías: qué es, para qué se usa aquí y por qué

| Tecnología | Para qué se usa en este proyecto | Por qué esta y no otra |
|---|---|---|
| **Java 17** | Lenguaje | Es del stack de Spin y de dominio propio; records, `sealed interface`, text blocks y pattern matching dan código corto y expresivo; LTS |
| **Spring Boot 3.3.5** | Framework web y de configuración | Estándar de la industria en Java; autoconfiguración, actuator, validación y seguridad integrados |
| **Spring Web (MVC)** | Endpoints REST | Modelo simple y maduro; la espera al proveedor es I/O, un hilo por petición alcanza para el reto |
| **Bean Validation (Jakarta)** | Validación sintáctica de DTOs | Declarativa; separa formato (400) de reglas de negocio (422) |
| **Spring Data JPA + Hibernate 6.5** | Persistencia | Reduce código repetitivo; `Specification` permite filtros dinámicos parametrizados (sin inyección SQL) |
| **Flyway** | Migraciones de esquema | Esquema versionado y reproducible; Hibernate solo valida (`ddl-auto=validate`), nunca modifica |
| **PostgreSQL 16** | Base de datos principal | Relacional y transaccional; `UNIQUE` atómico para idempotencia; `NUMERIC` exacto para dinero; escala con réplicas y particionado. Spin lo usa |
| **H2 (modo PostgreSQL)** | Perfil `local` y suite rápida de tests | Cero dependencias para evaluar; la fidelidad completa la cubre Testcontainers |
| **Resilience4j 2.2.0** (retry + circuit breaker) | Resiliencia ante el proveedor | Librería ligera y probada; se compone programáticamente (sin AOP) para un orden explícito `Retry(CircuitBreaker(...))` |
| **RestClient + JDK HttpClient** | Cliente HTTP al proveedor | API moderna de Spring 6.1; el `HttpClient` del JDK da timeouts de conexión y lectura sin dependencias extra |
| **Spring Security** | API key, deny-by-default | Cadena de filtros estándar; la seguridad queda aislada en `infrastructure/security` |
| **springdoc-openapi 2.6.0** | Swagger UI / OpenAPI | Contrato visible y probable desde el navegador |
| **Spring Boot Actuator** | Health `liveness`/`readiness` | Probes de Kubernetes y chequeo rápido de vida |
| **JUnit 5 + Mockito** | Tests unitarios e integración | Estándar de facto |
| **MockMvc / MockRestServiceServer** | Probar API y adaptador HTTP sin red | Rápidos y deterministas |
| **Testcontainers** | PostgreSQL real en tests | Prueba lo que H2 no puede: migración, esquema y concurrencia reales. Se omite solo si no hay Docker |
| **Maven** | Build | Convención en el ecosistema Spring |
| **Docker (multi-etapa) + Compose** | Empaquetado y ejecución local | Imagen pequeña y usuario no root; un comando levanta PostgreSQL + API |
| **Kubernetes + Kustomize** | Despliegue (opcional) | Modularidad: base + componentes opt-in (autoescalado, red, backup) sin duplicar manifiestos |
| **kind** | Kubernetes local gratis | Probar K8s sin costo de nube |
| **GitHub Actions + Dependabot** | CI y dependencias | Build/tests/imagen en cada cambio; actualizaciones de seguridad automáticas |
| **Make + Bash** | Panel de control y scripts | Un solo punto de entrada (`make help`); backups con `pg_dump`/`pg_restore` |
| **Postman** | Demo visual para el equipo | Colección con tests y environments por modo de ejecución |

Alternativas consideradas: Go (descartado: el reto pide el lenguaje que mejor domines), MongoDB (el modelo es relacional y
se necesita unicidad atómica), WireMock (más pesado que un mock embebido para correr el reto), JWT en el servicio
(le corresponde al gateway/IdP), Kafka/outbox (sobre-ingeniería hoy; documentado como siguiente paso).

---

## 4. Qué hace cada componente

### 4.1 Dominio (`domain/`) — sin dependencias de frameworks
| Archivo | Responsabilidad |
|---|---|
| `Transaction` | Record inmutable. Transiciones `pending()`, `executed()`, `rejected()`, `failed()` devuelven una copia nueva. `matches()` compara el request para idempotencia. Trunca mensajes de error a 512 caracteres |
| `TransactionRules` | Reglas pre-proveedor, en este orden: moneda → decimales → mínimo → tope de débito |
| `TransactionType`, `TransactionStatus` | Enums de tipo y estado |
| `ProviderResult` (sealed) | `Approved` o `Rejected`: respuestas **definitivas** del proveedor |
| `TransactionRepository`, `ProviderGateway` | Puertos (interfaces) hacia persistencia y proveedor |
| `ProviderRequest` | Datos enviados al proveedor; su `transactionId` viaja como `Idempotency-Key` |
| `TransactionQuery`, `PageResult` | Filtros y paginación sin tipos de Spring |
| Excepciones | `BusinessRuleViolationException`, `ProviderUnavailableException`, `DuplicateIdempotencyKeyException`, `IdempotencyKeyConflictException`, `TransactionNotFoundException` |

### 4.2 Aplicación (`application/`)
| Archivo | Responsabilidad |
|---|---|
| `TransactionService` | Orquesta: normaliza moneda → idempotencia → reglas → crea `PENDING` → llama al proveedor → guarda estado final. **Sin `@Transactional`**: no retiene conexión durante la llamada HTTP |
| `ExecuteTransactionCommand`, `ExecutionResult` | Entrada del caso de uso y resultado (con bandera `replayed`) |

### 4.3 Infraestructura (`infrastructure/`)
| Paquete / archivo | Responsabilidad |
|---|---|
| `persistence/TransactionEntity` | Mapeo JPA separado del dominio; implementa `Persistable` para evitar un SELECT antes de cada INSERT |
| `persistence/JpaTransactionRepository` | Adaptador del puerto; `saveAndFlush` hace que la violación de `UNIQUE` salte en el momento y se traduzca a `DuplicateIdempotencyKeyException` |
| `persistence/TransactionSpecifications` | Construye el `WHERE` dinámico con filtros opcionales, siempre parametrizado |
| `provider/HttpProviderGateway` | Traduce el contrato HTTP del proveedor a `ProviderResult` / `ProviderUnavailableException` |
| `provider/ResilientProviderGateway` | Decorador: `Retry( CircuitBreaker( HTTP ) )`. Con el circuito abierto falla rápido y no reintenta |
| `provider/ProviderConfig`, `ProviderProperties` | Construye el cliente, timeouts, retry y breaker desde configuración |
| `provider/MockProviderController` | Proveedor simulado con escenarios por sufijo de cuenta (solo con `provider.mock.enabled=true`) |
| `security/*` | Filtro de API key, cadena de seguridad, respuesta 401 en RFC 7807 |
| `config/*` | Cableado de beans y metadatos OpenAPI |

### 4.4 API (`api/`)
| Archivo | Responsabilidad |
|---|---|
| `TransactionController` | `POST /transactions`, `GET /transactions`, `GET /transactions/{id}`; mapea resultado → código HTTP |
| `dto/*` | Request/response y página; `NON_NULL` en la respuesta |
| `ApiExceptionHandler` | Todos los errores como RFC 7807 con campo `code` estable |

### 4.5 Recursos, operación y herramientas
| Elemento | Responsabilidad |
|---|---|
| `db/migration/V1__create_transactions.sql` | Tabla, restricción única de idempotencia e índices |
| `application*.yml` | Configuración por entorno y perillas |
| `Dockerfile`, `docker-compose.yml` | Imagen multi-etapa no root; PostgreSQL + API; perfil opcional `backup` |
| `deploy/k8s/` | `base/` + `components/` (autoscaling, network-policy, backup) + `overlays/` (local, local-full, prod-example) |
| `scripts/` | `demo.sh`, `backup.sh`, `restore.sh` |
| `Makefile` | Panel de control (núcleo vs opcional) |
| `presentacion/` | Presentación HTML, guía paso a paso, colección Postman |

---

## 5. Comportamiento del sistema

### 5.1 Arranque
- Carga configuración (`application.yml` + perfil). Perfil `local` usa H2 y activa el mock del proveedor.
- **Fail-safe:** si no hay ninguna API key configurada, la aplicación **se niega a arrancar**.
- Flyway aplica `V1` y Hibernate solo **valida** el esquema; si no coincide, falla al arrancar.
- Se registra cuántas API keys se cargaron (nunca su valor).
- Expone `/actuator/health/liveness` y `/readiness` para las probes.

### 5.2 `POST /transactions`
Orden exacto:
1. **Autenticación** (`X-API-Key`) → 401 si falta o es inválida.
2. **Validación de formato** → 400 con detalle por campo.
3. **Idempotencia**: si hay `Idempotency-Key` y ya existe → 200 con la original, o 409 si el payload difiere.
4. **Reglas de negocio** → 422; **no se toca la BD ni el proveedor**.
5. **Persistir `PENDING`** (commit corto).
6. **Llamar al proveedor** (timeouts, reintentos, circuit breaker).
7. **Persistir el estado final** y responder.

| Resultado | HTTP | `status` | Contenido relevante |
|---|---|---|---|
| Aprobada | 201 | `EXECUTED` | `providerTransactionId`, `balanceAfter` |
| Rechazada por el proveedor | 201 | `REJECTED` | `errorCode`, `errorMessage` (respuesta definitiva, se registra) |
| Proveedor no disponible | 502 | `FAILED` | `errorCode=PROVIDER_UNAVAILABLE`; resultado **desconocido** |
| Repetición con la misma key | 200 | el de la original | misma transacción, el proveedor no se vuelve a llamar |
| Misma key con otro payload | 409 | — | `IDEMPOTENCY_KEY_CONFLICT` |

Ejemplo de respuesta aprobada:
```json
{
  "id": "0b6c6f0e-5a4e-4a49-8f9b-3f2f3f7d9a11",
  "accountId": "acc-123456",
  "type": "CREDIT",
  "amount": 1500.00,
  "currency": "MXN",
  "description": "Transferencia recibida",
  "status": "EXECUTED",
  "providerTransactionId": "txn-1a2b3c",
  "balanceAfter": 5500.00,
  "createdAt": "2026-10-21T21:30:00.123Z"
}
```

### 5.3 Reglas de negocio
| Regla | Condición de rechazo | Código (HTTP 422) |
|---|---|---|
| Moneda | distinta de `MXN` (se normaliza a mayúsculas y sin espacios) | `UNSUPPORTED_CURRENCY` |
| Monto mínimo | `amount <= 1.00` (1.00 se rechaza) | `AMOUNT_TOO_LOW` |
| Tope de débito | `DEBIT` con `amount > 10,000.00` (10,000.00 pasa) | `DEBIT_LIMIT_EXCEEDED` |
| Decimales | más de 2 | `INVALID_AMOUNT_PRECISION` (en el HTTP lo detecta antes la validación de formato → 400) |
| Crédito | sin límite superior | — |

### 5.4 Idempotencia
| Situación | Resultado |
|---|---|
| Sin `Idempotency-Key` | Siempre crea una transacción nueva |
| Key nueva | Crea y ejecuta (201 / 502) |
| Key existente, mismo payload | 200 con la original; **proveedor no se llama** |
| Key existente, payload distinto | 409 |
| Dos peticiones simultáneas con la misma key | Una gana por la restricción `UNIQUE`; la otra recibe la transacción ganadora (200) |

El árbitro es la base de datos, no memoria local: funciona con N réplicas.

### 5.5 Proveedor y resiliencia
Traducción de respuestas (`HttpProviderGateway`):
| Respuesta del proveedor | Resultado interno |
|---|---|
| Cuerpo `REJECTED` (con cualquier status) | `Rejected` (definitivo) |
| 2xx con `transactionId` y `balance` | `Approved` |
| 2xx malformado | Caída |
| 5xx sin cuerpo `REJECTED` | Caída |
| 4xx sin cuerpo `REJECTED` (p. ej. 401/404) | `Rejected(PROVIDER_ERROR)` (reintentar no ayuda) |
| Error de red / timeout | Caída |

Política (por defecto, configurable): conexión 1 s, lectura 2 s; 3 intentos con backoff exponencial (200 ms ×2) y
jitter; circuit breaker abre con ≥50 % de fallas sobre ≥10 llamadas (ventana de 20), 10 s abierto, 3 llamadas de prueba.
Solo cuentan como falla las caídas; **un rechazo nunca se reintenta ni afecta al breaker**.

### 5.6 `GET /transactions` y `GET /transactions/{id}`
- Filtros opcionales: `accountId`, `status`, `type`. Paginación: `page` (desde 1) y `limit` (1–100, default 20).
- Orden: más recientes primero (`createdAt desc, id desc`).
- Respuesta: `{ data, page, limit, totalElements, totalPages }`.
- `limit` fuera de rango, `page < 1` o enums inválidos → 400. Id inexistente → 404.

### 5.7 Catálogo de errores
| `code` | HTTP | Cuándo |
|---|---|---|
| `VALIDATION_ERROR` | 400 | Body inválido (con `errors` por campo) |
| `INVALID_REQUEST` | 400 | Paginación inválida o `Idempotency-Key` inválida |
| `UNAUTHORIZED` | 401 | Falta la API key o es incorrecta |
| `TRANSACTION_NOT_FOUND` | 404 | Id inexistente |
| `IDEMPOTENCY_KEY_CONFLICT` | 409 | Misma key con otro payload |
| `AMOUNT_TOO_LOW`, `DEBIT_LIMIT_EXCEEDED`, `UNSUPPORTED_CURRENCY`, `INVALID_AMOUNT_PRECISION` | 422 | Reglas de negocio |
| `INTERNAL_ERROR` | 500 | Error inesperado (sin detalles internos) |

### 5.8 Datos
Tabla `transactions`: `id (UUID)`, `account_id`, `type`, `amount NUMERIC(19,2)`, `currency`, `description`, `status`,
`provider_transaction_id`, `balance_after`, `error_code`, `error_message`, `idempotency_key (UNIQUE)`, `created_at`,
`updated_at`. Índices: `(account_id, created_at DESC)` y `(status, created_at DESC)`.

### 5.9 Escenarios del proveedor mock
Se elige por el sufijo del `accountId`: normal (aprueba; cada cuenta inicia en 4000.00), `-nofunds` (422 `INSUFFICIENT_FUNDS`),
`-blocked` (403 `ACCOUNT_BLOCKED`), `-down` (503 siempre), `-flaky` (503 las dos primeras llamadas y luego OK), `-slow`
(responde a los 5 s; el timeout de lectura es 2 s).

### 5.10 ¿Qué pasa si…?
| Situación | Comportamiento |
|---|---|
| El proveedor rechaza por saldo | `REJECTED`, 201, sin reintentos, el breaker no se entera |
| El proveedor está caído | Reintenta, luego `FAILED` (502); tras varias fallas el breaker abre y responde de inmediato |
| El proveedor tarda más de 2 s | Timeout → trata como caída (`FAILED`); resultado ambiguo, requiere conciliación |
| El proceso muere tras crear `PENDING` | Queda una `PENDING` huérfana, detectable por estado/antigüedad; conciliación (diseñada, no implementada) |
| Llegan dos peticiones iguales a la vez | Una ejecuta, la otra recibe la misma transacción |
| Falta la API key al arrancar | La aplicación no arranca |
| Se cae un pod | Los demás atienden; no hay estado local |
| Se corrompen datos | Restauración desde backup (ver `OPERACION.md`) |

---

## 6. Pruebas

Total: **44 tests**. Estado: los 18 del núcleo se ejecutaron y pasaron; el resto se confirma con `make verify`.

| Clase | Tests | Qué prueba | Cómo |
|---|---|---|---|
| `TransactionRulesTest` | 8 | Límites exactos (1.00, 10,000.00), crédito sin tope, moneda, decimales | JUnit puro |
| `TransactionServiceTest` | 11 | Aprobada, rechazada, caída, error inesperado (nunca queda `PENDING`), reglas sin tocar proveedor ni BD, normalización de moneda, idempotencia (replay, conflicto, carrera), id reenviado al proveedor, `getById` inexistente | Fakes hechos a mano (sin Mockito) |
| `HttpProviderGatewayTest` | 7 | Cada fila de la traducción de respuestas, header `Idempotency-Key`, error de red | `MockRestServiceServer` |
| `ResilientProviderGatewayTest` | 4 | Reintenta hasta éxito, se rinde tras el máximo, no reintenta rechazos, circuito abierto falla sin llamar | Resilience4j real + fake |
| `TransactionControllerIT` | 12 | 201/502/422/400, validación por campo, idempotencia, filtros y paginación, 404, 401 (sin key y key incorrecta), health público | `@SpringBootTest` + MockMvc + H2, proveedor con Mockito |
| `PostgresIT` | 2 | Migración y mapeo en PostgreSQL real; 12 hilos con la misma key crean **una** transacción y llaman **una** vez al proveedor | Testcontainers (se omite sin Docker) |

Además: la colección de Postman (37 requests con tests) y `make demo` (8 escenarios) validan el sistema ya levantado.

**Cómo se garantiza que los tests puedan fallar:** ejercicio de mutación (quitar `UNIQUE` y comprobar que el test de
concurrencia falla) documentado en la guía y en `docs/USO-DE-IA.md`.

---

## 7. Operación y despliegue

| Pieza | Qué hace | Nivel |
|---|---|---|
| `make run` | API local con H2 y mock embebido | Núcleo |
| `make up` | PostgreSQL + API con Docker Compose | Núcleo |
| `make demo` / Postman | Recorrido de escenarios | Núcleo |
| `make key` | Genera una API key aleatoria en `.env` (ignorado por git) | Opcional |
| `make backup` / `make restore` | `pg_dump` verificado con `pg_restore --list` / restauración a una BD aparte | Opcional |
| `make k8s-local` / `k8s-local-full` | kind: núcleo / con autoescalado, PDB, NetworkPolicy y CronJob de backup | Opcional |
| CI + Dependabot | Build, tests, imagen; actualizaciones de dependencias | Opcional (en GitHub) |
| `make evidencia` | Guarda logs reales de `mvn verify` y de la demo en `./evidencias` | Apoyo a la entrega |
| `make pre-entrega` | Falla si quedan datos por completar y corre `mvn verify` | Entrega |

Detalle de capacidad, escalado y backups: [`OPERACION.md`](OPERACION.md). Matriz de costo/requisitos: [`OPCIONALES.md`](OPCIONALES.md).

---

## 8. Decisiones y alternativas descartadas (resumen)

| Decisión | Alternativa descartada | Razón |
|---|---|---|
| `PENDING` antes del proveedor | Persistir solo al final | No se pierde una operación si el proceso muere a mitad |
| Idempotencia con `UNIQUE` | `if` en memoria o cache local | Correcta con N réplicas |
| Rechazo = valor, caída = excepción | Todo fallo = `REJECTED` | Un saldo insuficiente no significa proveedor enfermo; no debe reintentarse ni abrir el breaker |
| Sin `@Transactional` en el servicio | Transacción única de punta a punta | No retener conexión durante la llamada HTTP |
| Resiliencia por decorador | Anotaciones con AOP | Orden explícito y testeable; sin fallbacks reintentados por error |
| API key en el servicio | JWT dentro del servicio | Es servicio-a-servicio; OAuth2 corresponde al gateway |
| Opcionales aislados | Todo activo por defecto | El núcleo siempre arranca; el costo es decisión de quien lo prueba |

---

## 9. Límites conocidos y siguientes pasos
- **Carga a escala no validada:** el dimensionamiento de millones de transacciones/día es un modelo por cálculo; existe una prueba de carga opcional (`make load`, k6) para medirlo. Ver `OPERACION.md` §7.
- **Conciliación** de `PENDING`/`FAILED` contra el proveedor: diseñada, no implementada.
- **Métricas y alertas** (Prometheus), logs estructurados y trazas: no implementadas.
- El mock **no respeta** `Idempotency-Key`; un proveedor real debería.
- `client_id` por transacción (auditoría) y rate limiting por cliente: pendientes (gateway).
- Paginación offset: a gran escala migrar a cursor; tabla grande: particionar por fecha.
- Outbox + Kafka para publicar eventos: siguiente paso natural.

## 10. Mapa de documentación
| Documento | Contenido |
|---|---|
| [`README.md`](../README.md) | Cómo ejecutar y probar, resumen |
| [`docs/ARQUITECTURA.md`](ARQUITECTURA.md) | Diagramas y razonamiento de diseño |
| [`docs/SEGURIDAD.md`](SEGURIDAD.md) | Amenazas y controles |
| [`docs/OPERACION.md`](OPERACION.md) | Capacidad, escalado, backups, runbook |
| [`docs/OPCIONALES.md`](OPCIONALES.md) | Núcleo vs opcional, costos, perillas |
| [`docs/USO-DE-IA.md`](USO-DE-IA.md) | Prompts, decisiones, validaciones y pruebas |
| [`presentacion/`](../presentacion/) | Presentación, guía paso a paso, Postman |
