# Operación: crecimiento, backups y observabilidad

## 1. Dimensionamiento (orden de magnitud)

Supuestos explícitos: **5 M transacciones/día**, pico = 10× el promedio, latencia del proveedor ≈ 300 ms.

| Magnitud | Cálculo | Resultado |
|---|---|---|
| Promedio | 5 000 000 / 86 400 s | ≈ 58 tx/s |
| Pico | 58 × 10 | ≈ 580 tx/s |
| Peticiones en vuelo | 580 tx/s × 0.3 s (ley de Little) | ≈ 175 concurrentes |
| Escrituras en BD | 2 por transacción (INSERT + UPDATE) | ≈ 1 160 escrituras/s en pico |
| Conexiones de BD ocupadas | 1 160/s × ~4 ms por escritura | ≈ 5 conexiones promedio |
| Crecimiento de datos | ~1 KB/fila con índices × 5 M | ≈ 5 GB/día ≈ 1.8 TB/año |

Lecciones: (1) el cuello de botella real es **esperar al proveedor**, no la base de datos; (2) por eso no se retiene
una conexión del pool durante la llamada HTTP (ver D1/D2 en `ARQUITECTURA.md`); (3) el almacenamiento es lo que
crece sin parar y obliga a particionar/archivar.

## 2. Crecimiento transparente (sin cambios de código)

| Capa | Cómo crece | Detalle |
|---|---|---|
| **Servicio** | Horizontal, automático | Sin estado ni sesiones → cualquier réplica atiende cualquier petición. `HorizontalPodAutoscaler` entre 3 y 20 réplicas (CPU 65 %). `PodDisruptionBudget` ≥ 2 y `maxUnavailable: 0` en despliegues: se escala y se despliega sin perder capacidad. |
| **Concurrencia** | Por pod | Cada pod absorbe cientos de peticiones concurrentes porque casi todo el tiempo es espera de I/O. Con Java 21 se puede activar `spring.threads.virtual.enabled=true` para miles de peticiones por pod. |
| **Idempotencia y carreras** | Correcta con N réplicas | El árbitro es la restricción `UNIQUE` en PostgreSQL, no memoria local. Cubierto por un test con 12 hilos concurrentes sobre PostgreSQL real (`PostgresIT`). |
| **Conexiones a BD** | PgBouncer (modo transaction) | `pods × DB_POOL_SIZE` no debe superar `max_connections`. Con 20 pods × 20 = 400 conexiones: poner PgBouncer delante o bajar el pool (con ~5 conexiones ocupadas en promedio, un pool de 5–10 por pod sobra). |
| **Lecturas** | Réplica de lectura | `GET /transactions` es candidato a una réplica (aceptando lag). Requiere un segundo `DataSource` de solo lectura: cambio acotado en `infrastructure/persistence`. |
| **Almacenamiento** | Particionado por fecha + archivo | Particionar `transactions` por mes (`created_at`) y archivar/eliminar particiones viejas. **Cuidado:** en una tabla particionada, `UNIQUE(idempotency_key)` debe incluir la clave de partición. Solución: una tabla pequeña `idempotency_keys` (con TTL de, p. ej., 7 días) o Redis para la unicidad, y la tabla grande solo para consulta. |
| **Paginación** | Cursor (keyset) | Cambiar `OFFSET` por `WHERE (created_at, id) < (:last_created, :last_id)` usando el índice existente. |
| **Proveedor** | Aislado por circuit breaker | Si el proveedor cae, el servicio falla rápido (502) y no consume hilos; al recuperarse, el breaker se cierra solo. |
| **Eventos** | Outbox + Kafka (cuando haga falta) | Para publicar `executed/rejected/failed` sin perder eventos: escribir el evento en la misma transacción de BD que el estado y un relay lo publica. No se añade hoy para no sobre-ingenierizar. |

### Despliegue en Kubernetes (opcional, modular)
`deploy/k8s/` usa Kustomize con **núcleo + componentes opt-in**, así cada capacidad se enciende o apaga sin editar manifiestos:

| Pieza | Qué contiene | Obligatoria |
|---|---|---|
| `base/` | Deployment (probes, `preStop` + *graceful shutdown*, límites, contexto de seguridad endurecido), Service, ConfigMap | Sí (si usas K8s) |
| `components/autoscaling` | HPA 3–20 réplicas + PodDisruptionBudget | No |
| `components/network-policy` | Solo el ingress controller llega a la API | No |
| `components/backup` | PVC + CronJob de `pg_dump` verificado cada 6 h | No |
| `overlays/local` | kind/minikube gratis: PostgreSQL desechable, proveedor mock, 2 réplicas | Demo |
| `overlays/local-full` | `local` + los tres componentes | Demo |
| `overlays/prod-example` | Ejemplo con BD gestionada externa y Secret creado fuera del repo | Solo referencia |

Probar sin costo: `make k8s-local` o `make k8s-local-full` (requiere Docker, kind y kubectl). Ver `docs/OPCIONALES.md`.

## 3. Backups y recuperación

**Objetivos propuestos:** RPO ≤ 5 min, RTO ≤ 1 h. En un sistema financiero perder transacciones confirmadas no es opción.

| Capa | Mecanismo | Protege contra | RPO |
|---|---|---|---|
| 1. Alta disponibilidad | Réplica síncrona/asíncrona con *failover* automático (Patroni, o el servicio gestionado multi-AZ) | Caída de nodo/zona | ~0 – segundos |
| 2. Recuperación a un punto en el tiempo (**PITR**) | Backup base + archivado continuo de WAL a almacenamiento de objetos (pgBackRest/WAL-G, o PITR del servicio gestionado) | Borrado/corrupción lógica ("alguien hizo un UPDATE sin WHERE") | minutos |
| 3. Backup lógico | `scripts/backup.sh` / CronJob cada 6 h (`pg_dump -Fc`, verificado con `pg_restore --list`, retención 7 días) | Red de seguridad independiente del motor y portabilidad | hasta 6 h |

Reglas que importan más que la herramienta:
1. **Un backup no probado no existe.** Hacer un *restore drill* mensual: `scripts/restore.sh <dump> --yes` sobre una
   BD de pruebas y comparar conteos por estado (el script ya imprime `count(*) GROUP BY status`).
2. Backups **cifrados**, en otra cuenta/región, con retención y copia inmutable (protección contra ransomware).
3. Alertar si el último backup exitoso tiene más de N horas (el CronJob en K8s expone el fallo vía Job fallido).
4. Las migraciones (Flyway) son parte de la recuperación: el esquema es reproducible desde el repositorio.

### Procedimiento de restauración (resumen)
```bash
# 1) Crear una BD vacía de destino
createdb -h $PGHOST -U $PGUSER transactions_restore
# 2) Restaurar
PGDATABASE=transactions_restore ./scripts/restore.sh backups/transactions_YYYYMMDDTHHMMSSZ.dump --yes
# 3) Verificar conteos y, si es una recuperación real, apuntar DB_URL a la BD restaurada
```

## 4. Conciliación (pendiente de implementar, diseñada)

Un job periódico (CronJob) que busque `PENDING` con más de X minutos y todas las `FAILED`, y consulte al proveedor
por el `id` de la transacción (enviado como `Idempotency-Key`):
- el proveedor la ejecutó → pasar a `EXECUTED` con su saldo y `providerTransactionId`;
- el proveedor no la conoce → reintentar la ejecución (segura gracias a la idempotencia) o marcar `FAILED` definitivo.
Esto cierra el caso del timeout ambiguo (D4). El índice `(status, created_at)` ya existe para esta consulta.

## 5. Observabilidad (roadmap concreto)

Hoy: logs con id de transacción en advertencias, `/actuator/health` (liveness/readiness) para las probes.
Siguiente paso, en orden de valor:
1. Métricas Micrometer → Prometheus: `transactions_total{type,status}`, latencia del proveedor (histograma),
   estado del circuit breaker, reintentos, uso del pool de conexiones.
2. Alertas: tasa de `FAILED` > 1 %, circuit breaker abierto > 1 min, `PENDING` envejecidos, p99 de latencia.
3. Logs estructurados (JSON) con *correlation id* propagado al proveedor.
4. Trazas distribuidas (OpenTelemetry) cliente → API → proveedor.

## 6. Runbook rápido

| Síntoma | Causa probable | Qué hacer |
|---|---|---|
| Muchos `502` | Proveedor caído o lento; circuit breaker abierto | Revisar estado del proveedor; el breaker se recupera solo (half-open cada 10 s). Las `FAILED` se concilian después. |
| Latencia alta, CPU baja | Proveedor lento (hilos esperando) | Revisar latencia del proveedor; el timeout de 2 s acota el daño. |
| `Timeout waiting for connection` | Pool agotado o `max_connections` alcanzado | Revisar `pods × pool` vs `max_connections`; PgBouncer. |
| Transacciones en `PENDING` viejas | Pod murió a mitad de flujo | Ejecutar conciliación (sección 4). |
| Pods no arrancan: "No API key configured" | Falta `SECURITY_API_KEYS` | Comportamiento deseado (fail-safe); revisar el Secret. |

## 7. Validación de carga (estado honesto)

**Estado actual: no se ha ejecutado una prueba de carga.** Los números de la sección 1 son un **modelo por cálculo** con
supuestos explícitos, no mediciones. Lo que sí existe: un test de concurrencia de idempotencia (12 hilos sobre PostgreSQL
real, `PostgresIT`) y el diseño orientado a volumen. "Diseñado para alto volumen" es correcto; "validado a millones por día"
**todavía no**, y así debe presentarse hasta tener mediciones.

### Cómo validarlo (opcional, gratis, local)
```bash
PROVIDER_MOCK_LATENCY_MS=300 make up          # proveedor simulado con 300 ms de latencia, como en el modelo
make load PROFILE=avg                          # ~58 tx/s sostenidos (promedio de 5 M/día)
make load PROFILE=peak                         # rampa a ~580 tx/s (pico de 10x)
make load PROFILE=peak RATE=300 DURATION=5m    # ajustar a lo que soporte tu máquina
```
Script: `scripts/load/k6-transactions.js` (k6 en Docker). Umbrales: errores < 1 %, p95 < 1 s, checks > 99 %.

### Qué comprobar después de cada corrida
| Comprobación | Cómo | Esperado |
|---|---|---|
| Throughput alcanzado y latencia p95/p99 | Resumen de k6 | Cumple los umbrales |
| Consistencia | `SELECT status, count(*) FROM transactions GROUP BY status;` | Sin `PENDING` huérfanas |
| Saturación | Hilos de Tomcat (`SERVER_MAX_THREADS`), pool (`DB_POOL_SIZE`), CPU | Sin esperas de conexión |
| Circuit breaker bajo carga | Repetir con una cuenta `-down` mezclada | Falla rápido, sin colapsar hilos |

### Qué NO demuestra una corrida local
- Millones por día **sostenidos** (haría falta una prueba de resistencia de horas) ni el comportamiento de la BD tras
  meses de datos (crecimiento, vacuum, particionado).
- El rendimiento con un proveedor real, red real, ni la conmutación por falla de la BD.
- Una laptop con Docker no equivale a producción: reporta siempre hardware, fecha y parámetros junto a los resultados.
- Ojo con el cuello de botella de hilos: 580 tx/s × 0.3 s ≈ 175 peticiones en vuelo; con `SERVER_MAX_THREADS=200` una sola
  instancia está al límite. Por eso el diseño contempla varias réplicas.
