# Qué es núcleo y qué es opcional (y cómo controlarlo)

Principio: **evaluar este proyecto no requiere Kubernetes, nube ni gastar nada.** El núcleo cumple el reto completo.
Todo lo demás existe, funciona y se puede demostrar, pero **solo se ejecuta si lo pides**.

## Matriz de capacidades

| Capacidad | Nivel | Cómo se activa | Costo | Requiere |
|---|---|---|---|---|
| API `POST/GET /transactions`, reglas de negocio, proveedor mock, persistencia | **NÚCLEO** | `make run` | $0 | JDK 17+, Maven |
| Misma API con PostgreSQL real | **NÚCLEO** | `make up` | $0 | Docker |
| Tests unitarios y de integración | **NÚCLEO** | `make test` / `make verify` | $0 | JDK, Maven |
| Demo guiada (8 escenarios) | **NÚCLEO** | `make demo` | $0 | `curl` |
| Idempotencia, reintentos, circuit breaker, API key | Incluido en el núcleo (ON) | siempre; perillas abajo | $0 | — |
| Test contra PostgreSQL real (Testcontainers) | Núcleo condicional | corre en `make verify` si hay Docker; si no, se omite solo | $0 | Docker |
| Backup lógico verificado | OPCIONAL | `make backup` (tras `make up`) | $0 | Docker |
| Restore drill (a una BD aparte, no toca la original) | OPCIONAL | `make restore FILE=backups/….dump` | $0 | Docker |
| Kubernetes local (núcleo) | OPCIONAL | `make k8s-local` | $0 (local) | Docker, kind, kubectl |
| Kubernetes local + autoescalado, PDB, NetworkPolicy, CronJob de backup | OPCIONAL | `make k8s-local-full` | $0 (local) | + metrics-server para ver el HPA trabajar |
| Despliegue en nube real | OPCIONAL, **no ejecutado** | `deploy/k8s/overlays/prod-example` (ejemplo) | Variable (cluster + BD gestionada) | Cuenta cloud |
| CI (build + tests + imagen) | OPCIONAL | automático al subir a GitHub | $0 (plan gratuito) | GitHub |
| Actualizaciones de dependencias | OPCIONAL | Dependabot, automático en GitHub | $0 | GitHub |

`make help` imprime esta lista con los comandos. Ningún objetivo opcional corre por defecto ni como efecto
colateral de uno del núcleo.

## Perillas de comportamiento (variables de entorno)

Sirven para demostrar el valor de cada mecanismo apagándolo y viendo qué pasa.

| Variable | Default | Para qué sirve en una demo |
|---|---|---|
| `PROVIDER_RETRY_MAX_ATTEMPTS` | `3` | Ponla en `1` y repite el escenario `-flaky`: sin reintentos termina `FAILED`. |
| `PROVIDER_READ_TIMEOUT` | `2s` | Súbela a `10s` y el escenario `-slow` ya no hace timeout. |
| `PROVIDER_CB_WINDOW` / `PROVIDER_CB_MIN_CALLS` | `20` / `10` | Súbelas mucho para "apagar" el circuit breaker y compara el tiempo de respuesta con `-down`. |
| `PROVIDER_CB_OPEN_FOR` | `10s` | Cuánto tarda el breaker en probar de nuevo. |
| `SPRINGDOC_ENABLED` | `true` | `false` en producción: oculta Swagger. |
| `SECURITY_API_KEYS` | (obligatoria) | Varias separadas por coma = rotación sin downtime. |
| `DB_POOL_SIZE` | `20` | Dimensionar contra `max_connections`. |

Ejemplo: `PROVIDER_RETRY_MAX_ATTEMPTS=1 make up` y luego `make demo`: el paso 5 pasa de EXECUTED a FAILED.

## Lo que se decidió NO activar por defecto, y por qué
- **Nube real:** costo y dependencia de cuenta; el reto no la exige y la lógica es idéntica en kind.
- **Autoescalado y NetworkPolicy:** agregan requisitos del cluster (metrics-server, CNI con soporte); son componentes
  separados para que el despliegue básico siempre arranque.
- **Backups:** son una capacidad operativa, no del flujo de negocio; se prueban con un comando cuando se quieren mostrar.
