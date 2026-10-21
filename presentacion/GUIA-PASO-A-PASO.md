# Guía para presentar el proyecto, paso a paso

Todo lo que necesitas está en esta carpeta. **Nada de lo opcional es obligatorio**: si algo falla, hay plan B.

## A. Preparación (la víspera, 30–45 min)

1. **Verifica que compila y pasa:** `make verify` (con Docker encendido para que corra el test de PostgreSQL real).
2. **Levanta y prueba el núcleo:** `make up`, luego `make demo`. Debes ver los 8 escenarios con los códigos esperados.
3. **Importa la colección de Postman** (`presentacion/postman/`, instrucciones en su `LEEME.md`), selecciona el
   environment que corresponda y corre la carpeta 1 para confirmar que todo está en verde. Es la forma más visual de
   hacer la demo ante el equipo.
4. **Decide qué opcionales mostrarás** (ninguno es obligatorio) y pruébalos una vez:
   - Backups: `make backup` → aparece un `.dump` en `./backups`; `make restore FILE=backups/<archivo>.dump`.
   - Kubernetes local: `make k8s-local-full` (requiere Docker, kind, kubectl) y luego
     `kubectl -n transactions port-forward svc/transactions-api 8080:80` (API key: `local-k8s-key`).
5. **Completa tus datos reales** en `docs/USO-DE-IA.md`, el README y `presentacion/index.html`
   (tiempos, qué verificaste, tus ejercicios). `make pre-entrega` te muestra exactamente qué falta.
6. Ensaya la presentación **en voz alta** con el cronómetro (10 min) y las preguntas de tu guía privada.
7. Abre `presentacion/index.html` en el navegador (funciona sin internet). Teclas: `←` `→` navegan, `N` muestra tus
   notas del orador, `P` imprime o genera PDF con todas las diapositivas y notas.

## B. Justo antes de presentar (5 min)

- [ ] `make up` corriendo y `curl localhost:8080/actuator/health/readiness` responde `UP`
- [ ] Terminal con fuente grande; `make demo` probado hace menos de una hora
- [ ] Navegador con la presentación (diapositiva 1) y Swagger abierto en otra pestaña (`/swagger-ui.html`)
- [ ] Plan B listo: `make run` si Docker falla; el PDF de la presentación si falla el navegador

## C. Guion (10 minutos) — diapositivas de `presentacion/index.html`

| Min | Diap. | Qué haces |
|---|---|---|
| 0:00–1:00 | 1–3 | Pitch: dinero → nunca duplicar, nunca perder. Muestra la separación núcleo / opcional. |
| 1:00–3:00 | 4–6 | Arquitectura, flujo y estados. Insiste en "todo apunta al dominio" y en no usar `@Transactional`. |
| 3:00–5:00 | 7–9 | Las dos decisiones clave (PENDING e idempotencia) y rechazo ≠ caída. |
| 5:00–7:00 | 14 | **Demo** en Postman (o en la terminal con `make demo`). Si hay tiempo: `PROVIDER_RETRY_MAX_ATTEMPTS=1` y repetir el paso 5. |
| 7:00–8:00 | 10–13 | Seguridad, escalado con números, operación opcional, testing (una frase por tema). |
| 8:00–9:00 | 15 | Uso de IA: cómo, por qué esos tiempos, qué hiciste tú. Solo verdades. |
| 9:00–10:00 | 16–17 | Límites honestos, siguientes pasos y una pregunta de vuelta. |

Si te dan 5 minutos en lugar de 10: diapositivas 1, 4, 5, 7, 8, 9 y la demo.

## D. Qué mostrar según lo que pregunten

| Si preguntan por… | Abre… |
|---|---|
| Cómo está organizado el código | `docs/ARQUITECTURA.md` (diagramas) y `src/main/java/.../domain` |
| Seguridad | `docs/SEGURIDAD.md` y `infrastructure/security/` |
| Cómo escala | `docs/OPERACION.md` (números) y diapositiva 11 |
| Costos / nube / Kubernetes | `docs/OPCIONALES.md` (matriz con costo y requisitos) |
| Backups | `scripts/backup.sh`, `make backup`, `docs/OPERACION.md` §3 |
| Uso de IA | `docs/USO-DE-IA.md` (fases, tiempos, verificación) |

## E. Opcionales: cómo encender y apagar

| Quiero mostrar… | Comando | Para apagarlo |
|---|---|---|
| Solo lo esencial, sin Docker | `make run` | Ctrl+C |
| Con PostgreSQL real | `make up` | `make down` |
| Backup y restore | `make backup` · `make restore FILE=…` | no deja nada corriendo |
| Kubernetes local (núcleo) | `make k8s-local` | `make k8s-down` |
| K8s + autoescalado, PDB, NetworkPolicy, CronJob | `make k8s-local-full` | `make k8s-down` |
| Retries / timeout / breaker apagados | variables `PROVIDER_*` (ver `docs/OPCIONALES.md`) | quitar la variable |

## F. Si algo sale mal

| Problema | Solución |
|---|---|
| Docker no levanta | `make run` (H2 + mock embebido). Di: "el núcleo no depende de Docker". |
| `make demo` falla en un paso | Confirma que `API_KEY` coincide (local: `local-dev-key`; compose: `dev-key-change-me`; K8s: `local-k8s-key`). |
| Puerto 8080 ocupado | Detén el otro proceso o `docker compose down`. |
| Falla un test de integración | Revisa si es el de PostgreSQL y no hay Docker: se omite solo. Cualquier otro: es un bug real, no lo ocultes. |
| Te preguntan algo que no sabes | "No lo implementé; lo resolvería así…" y explica el enfoque. Decirlo con claridad suma. |

## G. Después de presentar
- Anota las preguntas que te hicieron y las que no supiste responder.
- Si es el caso, ajusta el README con aprendizajes y responde con cualquier mejora.
