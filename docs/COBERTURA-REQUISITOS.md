# Cobertura de requisitos del reto

Matriz punto por punto de `Backend_Challenge.pdf`. Primero **lo que pidió el reto** (núcleo); al final, **lo que se agregó como plus**.

Estados: ✅ implementado · 🧪 implementado y con test escrito, **pendiente de ejecutar en tu máquina** (`make verify`) · ⚠️ desviación consciente (explicada).

> Honestidad sobre la verificación: los tests de dominio y servicio (19) se ejecutaron con un arnés solo-JDK. Los de
> Spring (controlador, Postgres/Testcontainers, cliente HTTP, resiliencia) están escritos pero no se ejecutaron en el entorno
> donde se generó el código. Antes de entregar corre `make verify` y `make evidencia` y guarda el resultado en `evidencias/`.

---

## A. Lo que pidió el reto (núcleo)

### A1. Capacidades del servicio

| # | Requisito del PDF | Estado | Con qué se hizo | Dónde / prueba |
|---|---|---|---|---|
| 1 | Recibir solicitudes CREDIT/DEBIT | ✅ | Spring Web + Bean Validation | `api/TransactionController`, `api/dto/CreateTransactionRequest`; `TransactionControllerIT.unknownTransactionTypeReturns400` 🧪 |
| 2 | Ejecutar contra proveedor externo | ✅ | Puerto `ProviderGateway` + adaptador `RestClient` | `infrastructure/provider/HttpProviderGateway`; `HttpProviderGatewayTest` (7) 🧪 |
| 3 | Persistir el resultado | ✅ | JPA + Flyway + PostgreSQL | `infrastructure/persistence/*`, `db/migration/V1__create_transactions.sql`; `PostgresIT` 🧪 |
| 4 | Endpoint de consulta | ✅ | Spring Data + Specifications | `GET /transactions`; `TransactionControllerIT.listSupportsFiltersAndPagination` 🧪 |
| 5 | Alto volumen / escalabilidad | ✅ (modelo) | Servicio sin estado, sin `@Transactional` alrededor del HTTP, índices, pool acotado, N réplicas | `docs/OPERACION.md` §capacidad. **Calculado, no medido**; `make load` (k6) para medirlo |
| 6 | No gestionar balances | ✅ | El saldo solo se guarda (`balanceAfter`) tal como lo devuelve el proveedor | `domain/Transaction` |

### A2. `POST /transactions`

| Requisito | Estado | Con qué | Dónde / prueba |
|---|---|---|---|
| Campos: accountId, type, amount, currency, description | ✅ | Record + anotaciones de validación | `CreateTransactionRequest`; `malformedRequestReturns400WithFieldErrors` 🧪 |
| Paso 1: validar el request | ✅ | Bean Validation → 400 con errores por campo | `ApiExceptionHandler` |
| Paso 2: reglas de negocio | ✅ | Dominio puro | `domain/TransactionRules`; `TransactionRulesTest` (8) ✅ ejecutado |
| Paso 3: llamar al proveedor | ✅ | `ProviderGateway` | `TransactionService.callProvider` |
| Paso 4: persistir con resultado y saldo | ✅ | `executed(...)`/`rejected(...)`/`failed(...)` | `TransactionServiceTest.approvedProviderResponseIsPersistedAsExecutedWithBalance` ✅ |
| Paso 5: retornar la transacción con estado | ✅ | `TransactionResponse` | `executedTransactionReturns201WithProviderData` 🧪 |
| Response: id, …, status, providerTransactionId, balanceAfter, createdAt | ✅ | Idéntico al ejemplo del PDF (+ `errorCode`/`errorMessage` si hubo rechazo o caída) | `TransactionResponse` |
| Orden persistir/proveedor | ⚠️ | Se guarda `PENDING` **antes** de llamar al proveedor y se actualiza después | Si el proceso muere tras mover dinero, no se pierde la operación. Documentado en README §6 |

### A3. `GET /transactions`

| Requisito | Estado | Dónde / prueba |
|---|---|---|
| Filtros `accountId`, `status`, `type` | ✅ | `TransactionSpecifications`; `listSupportsFiltersAndPagination` 🧪 |
| Paginación `page`, `limit` | ✅ (page desde 1, limit ≤ 100, orden `createdAt` desc) | `TransactionController`; `invalidPaginationReturns400` 🧪 |

### A4. Proveedor externo (mock)

| Requisito | Estado | Con qué | Dónde / prueba |
|---|---|---|---|
| Mockearlo "como consideres" | ✅ | Stub configurable dentro de la app (perfil `local`/compose), escenarios por sufijo de `accountId` | `MockProviderController` (`-nofunds`, `-blocked`, `-down`, `-flaky`, `-slow`) |
| Contrato `POST /provider/v1/execute` | ✅ | Request y response como el PDF | `HttpProviderGateway`, `ProviderRequest` |
| Respuesta exitosa 200 `APPROVED` + balance | ✅ | `ProviderResult.Approved` | `approvedResponseIsMappedAndRequestCarriesIdempotencyKey` 🧪 |
| Respuesta fallida 4XX–5XX con `REJECTED` | ✅ | `ProviderResult.Rejected` (definitivo, no se reintenta) | `rejectedBodyOn4xxIsMappedToRejected`, `rejectedBodyOn5xxIsStillADefinitiveRejection` 🧪 |
| Reflejar el resultado en lo persistido | ✅ | Estados `EXECUTED` / `REJECTED` / `FAILED` | `rejectedProviderResponseIsPersistedAsRejectedWithCode` ✅ |
| (Caso no cubierto por el PDF) 5xx sin cuerpo / red caída | ✅ | `ProviderUnavailableException` → `FAILED`, HTTP 502 | `unavailableProviderIsPersistedAsFailed` ✅, `providerOutageReturns502WithFailedTransaction` 🧪 |

### A5. Reglas de negocio (antes de llamar al proveedor)

| Regla | Estado | Prueba |
|---|---|---|
| Monto > 1.00 (1.00 se rechaza) | ✅ | `rejectsAmountEqualToMinimum`, `rejectsAmountBelowMinimumAndNegative`, `acceptsAmountJustAboveMinimum` ✅ |
| DEBIT ≤ 10,000.00; CREDIT sin límite | ✅ | `debitAboveLimitIsRejected`, `debitAtLimitIsAccepted`, `creditHasNoUpperLimit` ✅ |
| Solo MXN | ✅ | `onlyMxnIsSupported`, `currencyIsNormalizedToUpperCase` ✅ |
| Deben ocurrir antes del proveedor y devolver error descriptivo | ✅ | `businessRuleViolationNeverReachesProviderNorDatabase` ✅; `businessRuleViolationReturns422AndNeverCallsProvider` 🧪 (RFC 7807, `code` estable) |
| (Extra defensivo) máximo 2 decimales | ✅ | `rejectsMoreThanTwoDecimals` ✅ (evita redondeo silencioso de dinero) |

### A6. Requerimientos técnicos y entregables

| Requisito | Estado | Con qué / dónde |
|---|---|---|
| Lenguaje a elección | ✅ | Java 17 + Spring Boot 3.3 |
| Persistencia a elección, **justificada en el README** | ✅ | PostgreSQL (H2 en modo PostgreSQL para `local`/tests). Justificación: README §6 y `docs/ARQUITECTURA.md` |
| Tests unitarios (mínimo) | ✅ / 🧪 | 44 tests: 19 de dominio/servicio ejecutados ✅; 25 más escritos 🧪 |
| README con instrucciones para levantar y ejecutar | ✅ | `README.md` §1–2 (`make run`, `make up`) |
| README con decisiones de diseño | ✅ | `README.md` §6 |
| Repositorio Git | ✅ | Historial de commits convencionales; `.gitignore` limpio |
| **Nota de uso de IA en el README** | ✅ / ⏳ | `README.md` §14 + `docs/USO-DE-IA.md`. **Pendiente:** completar los `TODO-RICK` con tus horas y lo que verificaste personalmente |
| Entender y poder explicar cada línea | ⏳ (tuyo) | Material de estudio: `docs/PROYECTO-DETALLADO.md` y los archivos privados en `presentacion/privado/` (ignorados por Git) |

### A7. Qué valoran → dónde se ve

| Criterio | Evidencia |
|---|---|
| Código limpio y legible | Records inmutables, nombres de intención, `TransactionService` con un solo flujo lineal |
| Arquitectura en capas / inversión de dependencias | Hexagonal: `domain` y `application` sin Spring; puertos `TransactionRepository`, `ProviderGateway`; cableado en `ApplicationConfig` |
| Testing con mocks del proveedor | `TransactionServiceTest` (fake del puerto), `HttpProviderGatewayTest` (`MockRestServiceServer`), `ResilientProviderGatewayTest` |
| Cliente HTTP desacoplado + resiliencia (plus) | Puerto + adaptador; timeouts, `Retry(CircuitBreaker(HTTP))` con Resilience4j |
| Pragmatismo | Núcleo pequeño y gratuito; todo lo demás es opt-in (ver sección B) |
| Documentación | README + `docs/` |

---

## B. Plus (nuevo; no lo pedía el reto)

Cada línea dice si es parte de lo que corre por defecto o si es opt-in.

| Plus | Por qué existe | Se activa con |
|---|---|---|
| Idempotencia (`Idempotency-Key` + `UNIQUE`) | En un sistema de dinero un reintento del cliente no debe duplicar el movimiento | Por defecto (header opcional) |
| Seguridad: API key, deny-by-default, fail-safe sin keys | "Servicio financiero" sin auth no es defendible | Por defecto |
| Errores RFC 7807 con `code` estable | Contrato de error consumible por máquinas | Por defecto |
| `GET /transactions/{id}` | Consulta puntual natural | Por defecto |
| Flyway | Esquema versionado y reproducible | Por defecto |
| OpenAPI / Swagger UI (springdoc) | El PDF lo menciona como bienvenido | Por defecto en dev; `SPRINGDOC_ENABLED=false` en prod |
| Actuator / health | Probes y diagnóstico | Por defecto |
| Dockerfile multi-stage + Compose endurecido | PDF: "Dockerfile bienvenido" | `make up` |
| Backups de PostgreSQL | Recuperación | `make up-backup` / `make backup` (opt-in) |
| Kubernetes (Kustomize: base, HPA/PDB, NetworkPolicy, backup CronJob) | Escalamiento transparente | `make k8s-local` / `k8s-local-full` (opt-in, sin costo: kind local) |
| CI (GitHub Actions) + Dependabot | Calidad continua y dependencias al día | Automático en GitHub |
| Prueba de carga k6, escaneo Trivy | Validar capacidad y vulnerabilidades | `make load`, `make scan` (opt-in) |
| Colección Postman + presentación HTML + demo script | Facilitar la demo al equipo | `presentacion/`, `make demo` |
| Docs extra (`SEGURIDAD`, `OPERACION`, `ARQUITECTURA`, …) | Defender cada decisión | Lectura |

---

## C. Auditoría de necesidad (¿todo lo que hay se justifica?)

Revisión de los 95 archivos versionados.

**Código (50 archivos en `src/`)** — todo se usa. Resultado del barrido automático: 0 clases huérfanas (las que solo
aparecen en su propio archivo son puntos de entrada de Spring: controladores, `@Configuration`, handlers), 0 métodos
muertos. Se encontró y **eliminó 1 import sin uso** (`BigDecimal` en `TransactionService`) y una carpeta vacía creada
por error en `src/test`.

**Lo que queda por decisión, no por obligación** (el reto no lo pide; puedes quitarlo sin tocar el núcleo):

| Elemento | Veredicto | Razón |
|---|---|---|
| `deploy/k8s/` (17 archivos) | Mantener, opt-in | Stack de Spin incluye Kubernetes; no afecta el arranque normal. Si prefieres un repo más pequeño, es la carpeta de mayor tamaño que puedes quitar |
| `scripts/backup.sh`, `restore.sh` | Mantener | Pequeños, respaldan el requisito de recuperación |
| `docs/PROMPTS-DE-REVISION.md` | Mantener solo si **ejecutas** las revisiones | Sin evidencia de ejecución es un documento vacío de valor; si no vas a correrlos, bórralo y quita su enlace del README §14 |
| `docs/PROYECTO-DETALLADO.md` vs `ARQUITECTURA.md` vs `OPERACION.md` | Solapamiento parcial | `PROYECTO-DETALLADO` es el recorrido completo; las otras dos profundizan. Aceptable, pero si el repo debe verse más ligero, deja `README` + `ARQUITECTURA` + `SEGURIDAD` + `USO-DE-IA` |
| `presentacion/index.html`, `GUIA-PASO-A-PASO.md` | Mantener | Sirven para la demo, no para el código |
| `TODO-RICK` (3 archivos; `make pre-entrega` falla mientras existan) | **Debe resolverse antes de entregar** | `README.md`, `docs/USO-DE-IA.md`, `presentacion/index.html` |

**Regla de pragmatismo:** el núcleo (sección A) arranca con `make run` sin Docker, sin nube y sin costo. Nada de la sección B
es necesario para que funcione.

---

## D. Lo que falta de tu lado antes de entregar

1. `make verify` y `make evidencia` → guardar la salida en `evidencias/`.
2. Confirmar que el workflow de GitHub Actions quedó en verde.
3. Reemplazar los `TODO-RICK` (tiempos, qué verificaste tú).
4. Decidir si quitas los elementos marcados "opcional" de la sección C.
5. Ensayar la defensa: poder explicar el orden `PENDING` → proveedor → actualización, la idempotencia y la diferencia rechazo vs caída.
