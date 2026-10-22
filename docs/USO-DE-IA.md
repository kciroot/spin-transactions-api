# Uso de IA: dirección de ingeniería, decisiones, validación y pruebas

El enunciado permite usar IA y pide documentarlo. Este documento muestra **cómo dirigí el trabajo con la IA**: qué requisitos
no funcionales impuse, qué alternativas contrasté, qué decisiones tomé y cómo valido el resultado, con evidencia verificable.

> **Cómo leer este documento.** Lo escrito como hecho está respaldado por la conversación con la IA o por archivos del
> repositorio. Los campos `TODO-RICK` son resultados de mi propia ejecución (tiempos, comandos, resultados) que completo
> **después de correrlos**, con la evidencia que genera `make evidencia`. `make pre-entrega` falla mientras queden pendientes.

## 1. Modelo de trabajo: dirección con revisión
Trabajé como responsable técnico del resultado y usé la IA como ejecutora y asesora:

| Responsabilidad | Quién |
|---|---|
| Definir objetivo, prioridades, restricciones y criterios de calidad | **Yo** |
| Delegar la propuesta técnica y **auditarla**: contrastar con otra alternativa y exigir la justificación de cada componente y tecnología | **Yo** |
| Generar código, tests, manifiestos y documentación según esas restricciones | IA (Claude) |
| Verificar lo que su entorno permitía y declarar lo que no | IA (Claude) |
| Ejecutar, depurar en mi entorno, validar con evidencia y defender cada decisión | **Yo** |

Importante: **delegué la selección del stack a la IA y la audité** (contraste con otro plan, justificación por componente);
no la elegí yo por adelantado. Mi aporte está en las restricciones, el contraste, la priorización y la validación.

## 2. Requisitos no funcionales que impuse
| Requisito | Origen | Cómo se atendió | Dónde verificarlo |
|---|---|---|---|
| **Calidad de código** (limpio, capas, testing, pragmatismo) | Criterios del enunciado que aporté | Hexagonal, dominio sin framework, 44 tests, núcleo mínimo y opcionales aislados | `docs/ARQUITECTURA.md`, `docs/PROYECTO-DETALLADO.md` §6 |
| **Escalabilidad / alto volumen** | Enunciado ("millones de transacciones diarias") y mi pedido de "crecimiento transparente" | Servicio sin estado, idempotencia en la BD, sin retener conexiones durante la llamada externa, autoescalado opcional, dimensionamiento con números | `docs/OPERACION.md` |
| **Seguridad** | Mi pedido explícito de no olvidar la seguridad | API key en tiempo constante, deny-by-default, fail-safe, validación estricta, contenedor endurecido, Dependabot | `docs/SEGURIDAD.md` |
| **Recuperación y respaldo** | Mi pedido explícito de backups | Estrategia en 3 capas, scripts verificados, restore a BD aparte, CronJob opcional | `docs/OPERACION.md` §3 |
| **Resiliencia ante el proveedor** | Enunciado (plus) | Timeouts, reintentos con backoff, circuit breaker, separación rechazo/caída | `docs/PROYECTO-DETALLADO.md` §5.5 |
| **Control de costo** | Mi restricción: K8s y nube **opcionales** | Núcleo gratis; opcionales por comando; nube solo como ejemplo | `docs/OPCIONALES.md` |
| **Explicabilidad** | Mi pedido de "el porqué de cada componente" y de dominar el proyecto | Tabla de alternativas descartadas, guía de estudio, presentación | `docs/ARQUITECTURA.md`, `presentacion/` |
| **Operabilidad y demo** | Mi pedido de material para presentar y de demo visible para el equipo | Makefile, colección Postman con tests, guion paso a paso | `presentacion/` |

## 3. Bitácora de dirección (mis instrucciones, en orden)
Resumen fiel de lo que pedí, con la intención de ingeniería detrás de cada una:

| # | Mi instrucción | Intención de ingeniería | Qué entregó la IA |
|---|---|---|---|
| 1 | Subí el PDF: "vamos a dar solución... primero cubramos lo necesario y después el plus" | **Priorización**: núcleo verificable antes de las capas avanzadas | Diseño, proyecto Java/Spring, dominio y servicio con 18 tests |
| 2 | "Continuar" (dos veces) | Avanzar por entregas parciales, revisando cada una | Infraestructura, API y README inicial |
| 3 | "Comparemos vs lo que me dejó Gemini" + su plan | **Evaluación comparativa**: no comprometerme con una sola propuesta | Comparativa; se mantuvo el diseño y se sumó Testcontainers |
| 4 | "Sí, y no olvidemos la seguridad, el crecimiento transparente, backups y demás... el porqué de cada componente... tener el 100 % dominado" | **Requisitos no funcionales** (seguridad, escalamiento, recuperación) y **explicabilidad** | API key, Testcontainers, Kubernetes, backups, CI, docs y guía de estudio |
| 5 | "Si necesitamos k8 o nube con costo, dejarlos como opcionales... plus con control de poder realizarlo o no... uso de IA, tiempos, codificación" | **Restricción de costo** y control de lo avanzado | Makefile, componentes Kustomize opt-in, perfil de compose, presentación, guía paso a paso |
| 6 | "¿Dónde obtengo la key?" (dos veces) | **Prueba de usabilidad** del flujo de autenticación | Explicación y `make key` |
| 7 | "JSON para importar a Postman... todas las APIs... demo visible para el team" | Demo reproducible para el equipo | Colección Postman (37 requests con tests) y 3 environments |
| 8 | Pegué el log de arranque: "me genera unauthorized" | **Depuración con evidencia real** de mi entorno | Diagnóstico (key no coincidente), quitar un aviso innecesario, log de keys cargadas |
| 9 | "¿Se puede generar desde docker compose? ¿Cuál es la arquitectura?" | Reducir fricción y **verificar mi comprensión** del diseño | `make key` / `make show-key` y explicación |
| 10 | "Archivo detallado de todo, README más claro, el porqué de cada tecnología, prompts, validaciones y testing" | Material claro y defendible | `PROYECTO-DETALLADO.md`, README, este documento, `make evidencia` |

**Patrones de dirección que usé:** priorizar por niveles; contrastar antes de decidir; exigir el porqué de cada componente;
fijar restricciones (costo); pedir material para defender; aportar evidencia real al depurar.

## 4. Decisiones de ingeniería
| Decisión | Contexto | Resultado |
|---|---|---|
| Contrastar con otro plan antes de seguir | Había dos propuestas | Mantener la arquitectura y sumar Testcontainers; descartar mapear toda falla del proveedor a `REJECTED` |
| Exigir seguridad, escalamiento y backups además del reto | Entorno fintech de alto volumen | Implementado, y documentado lo que corresponde a la plataforma |
| Kubernetes y nube **opcionales** | Costo y complejidad para evaluar | `make k8s-local` gratis con kind; nube real solo como ejemplo |
| Demo en Postman para el equipo | Presentación visible | Colección + environments por modo |
| Aceptar Java/Spring Boot propuesto por la IA | Delegué la selección y la audité | TODO-RICK (mi razón: p. ej. coincide con el stack de Spin y lo conozco) |
| Postura sobre 201 en `REJECTED` y el orden `PENDING` | Se desvían del enunciado | TODO-RICK (mi opinión tras revisarlo; plantearla como pregunta al equipo) |

## 5. Revisiones adicionales con IA (plan de calidad)
Revisiones que ejecuto con la IA sobre el código ya generado, para endurecerlo antes de entregar. Los prompts están en
[`PROMPTS-DE-REVISION.md`](PROMPTS-DE-REVISION.md). Cada fila se completa **solo después de ejecutarla**.

| Revisión | Enfoque | Estado | Hallazgos y acción tomada |
|---|---|---|---|
| Seguridad | Amenazas, autenticación, validación, secretos, contenedor | TODO-RICK (pendiente de ejecutar) | TODO-RICK |
| Escalabilidad | Cuellos de botella, pool, concurrencia, crecimiento de datos | TODO-RICK (pendiente de ejecutar) | TODO-RICK |
| Calidad de código | Legibilidad, responsabilidades, naming, deuda técnica | TODO-RICK (pendiente de ejecutar) | TODO-RICK |
| Brechas de testing | Casos no cubiertos, pruebas que no pueden fallar | TODO-RICK (pendiente de ejecutar) | TODO-RICK |

## 6. Validación: qué se verificó y qué verifico yo

### 6.1 Verificado por la IA antes de entregarme el código
- Dominio y aplicación **compilados y ejecutados**: 19 tests en verde (arnés JDK, sin Spring).
- Sintaxis válida de YAML (compose, Kubernetes, CI), JSON (Postman) y scripts Bash; el `Makefile` se interpreta correctamente.
- La lógica de `make key` probada en una carpeta temporal (genera, no sobrescribe).
- El HTML de la presentación está bien formado.

### 6.2 **No** verificado por la IA (lo confirmo yo)
Compilación y ejecución de Spring/JPA/seguridad, adaptador HTTP, Resilience4j, tests de integración, Docker, Kubernetes y
Postman. Declararlo evita presentar como probado lo que no lo está.

### 6.3 Mi validación (evidencia que genero yo)
`make evidencia` guarda en `./evidencias/`: fecha (UTC), log y código de salida de `mvn verify`, log de la demo y commits.

| # | Validación | Cómo | Resultado esperado | Evidencia | Mi resultado |
|---|---|---|---|---|---|
| 1 | La aplicación arranca en mi máquina | `mvn spring-boot:run` | Flyway aplica V1 y JPA inicializa | Log de arranque del 2026-10-21 ~15:25 (-06:00): *"Successfully applied 1 migration… now at version v1"* y *"Initialized JPA EntityManagerFactory"* | **Hecho** (arranque observado) |
| 2 | Diagnóstico del 401 | `curl -i -H "X-API-Key: <key>" localhost:8080/transactions` | 200 con la key correcta | Salida del curl | TODO-RICK |
| 3 | Suite completa en verde | `make evidencia` | `mvn verify exit code: 0` | `evidencias/verify.log` | TODO-RICK |
| 4 | Demo de 8 escenarios | `make demo` con la app arriba | Códigos esperados en cada paso | `evidencias/demo.log` | TODO-RICK |
| 5 | Los tests pueden fallar (mutación) | Quitar `UNIQUE` en `V1__create_transactions.sql`, correr `PostgresIT` | El test de concurrencia **falla**; restaurar | Captura o log | TODO-RICK |
| 6 | Postman | Importar colección + environment, correr carpetas 1, 2, 3, 5, 6 | Todos los tests en verde | Captura del Runner | TODO-RICK |
| 7 | Opcional: backup/restore | `make backup` y `make restore FILE=...` | `.dump` creado y conteo por estado tras restaurar | Salida de consola | TODO-RICK (o "no demostrado") |
| 8 | Opcional: Kubernetes local | `make k8s-local-full` | `rollout status` OK; HPA/PDB/CronJob creados | `kubectl get ...` | TODO-RICK (o "no demostrado") |

## 7. Testing: qué existe y qué cubre
Detalle completo por clase en [`PROYECTO-DETALLADO.md` §6](PROYECTO-DETALLADO.md). Resumen:

| Nivel | Clase | Tests | Estado |
|---|---|---|---|
| Dominio | `TransactionRulesTest` | 8 | Ejecutado por la IA: en verde |
| Servicio | `TransactionServiceTest` | 10 | Ejecutado por la IA: en verde |
| Adaptador HTTP | `HttpProviderGatewayTest` | 7 | Por confirmar con `make verify` |
| Resiliencia | `ResilientProviderGatewayTest` | 4 | Por confirmar con `make verify` |
| API completa (H2) | `TransactionControllerIT` | 12 | Por confirmar con `make verify` |
| PostgreSQL real | `PostgresIT` | 2 | Por confirmar (requiere Docker) |
| Demo | `make demo` / Postman (37 requests) | 8 / 37 | Por confirmar |

## 8. Tiempos
La columna "sin IA" es una **estimación orientativa** de esfuerzo manual para un ingeniero con experiencia (no un dato
medido). La columna "real" es mi tiempo efectivo.

| Fase | Qué hizo la IA | Qué hice yo | Sin IA (est.) | Real |
|---|---|---|---|---|
| Análisis y diseño | Propuso arquitectura, estados, flujo e idempotencia | Definí alcance, contrasté con otra IA y decidí | 3–4 h | TODO-RICK |
| Dominio, servicio y tests unitarios | Código inicial y casos | Revisión línea por línea, ejercicios de modificación | 4–6 h | TODO-RICK |
| Persistencia, proveedor, resiliencia | Código de infraestructura | Ejecutar, depurar y verificar contra documentación | 5–7 h | TODO-RICK |
| API, errores, seguridad | Controller, errores RFC 7807, filtro de API key | Revisión de seguridad y de códigos HTTP; depuración del 401 | 3–4 h | TODO-RICK |
| Tests de integración | Escribió los tests | Correrlos, corregir entorno, mutación | 3–4 h | TODO-RICK |
| Docker, K8s, backups, CI | Manifiestos y scripts modulares | Probar los opcionales que decida mostrar | 4–6 h | TODO-RICK |
| Documentación y presentación | Borradores | Reescribir con mis palabras, ensayar | 4–6 h | TODO-RICK |
| **Total** | | | **26–37 h** | **TODO-RICK** |

La IA elimina la escritura repetitiva (DTOs, configuración, boilerplate, manifiestos) y acelera explorar alternativas; no
elimina la revisión, la depuración ni la comprensión. TODO-RICK (una frase con mi experiencia real: dónde tardé más y qué corregí).

## 9. Cómo se codificó (orden y por qué)
1. Dominio y aplicación sin framework (el riesgo de negocio) y verificados de forma aislada.
2. Infraestructura detrás de interfaces.
3. API y seguridad, que dependen de los códigos de error del dominio.
4. Pruebas de integración, incluida concurrencia sobre PostgreSQL real.
5. Operación (Docker, K8s, backups, CI) como capas opcionales sobre un núcleo que ya funcionaba.
6. Documentación y presentación al final, con las decisiones ya tomadas.

## 10. Riesgos de usar IA y mitigaciones
| Riesgo | Mitigación |
|---|---|
| Código que no compila o API inexistente | `make verify` y `make evidencia` en mi máquina; errores corregidos y documentados |
| Comprensión superficial | Ejercicios de modificación, simulacro de preguntas, explicar en voz alta |
| Decisiones plausibles pero mal justificadas | Alternativas descartadas y razones por escrito (`ARQUITECTURA.md`, `PROYECTO-DETALLADO.md` §8) |
| Presentar como probado lo no probado | Sección 5 separa lo verificado de lo pendiente, con evidencia |
| Sobre-ingeniería | Lo opcional está aislado y apagado por defecto |
| Seguridad (secretos, dependencias) | Sin secretos en el repo, `.env` ignorado, fail-safe sin API key, Dependabot |

## 11. Lo que hago yo antes de presentar
- [ ] Correr `make evidencia` y revisar que `verify.exitcode` sea `0` (TODO-RICK)
- [ ] Correr `make demo` y la colección de Postman, y entender cada escenario (TODO-RICK)
- [ ] Hacer el ejercicio de mutación y al menos 4 ejercicios de la guía de estudio (TODO-RICK)
- [ ] Completar los `TODO-RICK` de este documento y del README con datos reales (TODO-RICK)
- [ ] Probar los opcionales que voy a mostrar (cuáles: TODO-RICK)
- [ ] Ensayar la presentación en voz alta y las preguntas frecuentes (TODO-RICK)

## 12. Declaración (para el README)
Usar esta frase **solo después** de haber ejecutado y validado (sección 6.3). TODO-RICK: confirmar que es verdad y quitar esta nota.

> Usé Claude como asistente de ingeniería: yo definí el alcance y las restricciones (núcleo primero, plus opcional, sin
> costo de nube), contrasté su propuesta con el plan de otra IA y dirigí cada fase mediante prompts. La IA generó código,
> tests, manifiestos y documentación; yo ejecuté y validé el resultado (suite de tests, demo y colección Postman), documenté
> la evidencia en `evidencias/` y puedo explicar y defender cada decisión. Detalle en `docs/USO-DE-IA.md`.
