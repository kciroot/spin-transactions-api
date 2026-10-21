# Uso de IA: prompts, decisiones, validaciones y pruebas

El enunciado permite usar IA y pide documentarlo. Aquí se explica **cómo dirigí a la IA, qué decidí yo, cómo valido
y pruebo el resultado**, con evidencia verificable.

> **Cómo leer este documento.** Lo escrito como hecho está respaldado por la conversación con la IA o por archivos del
> repositorio. Los campos `TODO-RICK` son resultados de mi propia ejecución (tiempos, comandos, resultados) que completo
> **después de correrlos**, con la evidencia que genera `make evidencia`. `make pre-entrega` falla mientras queden pendientes.

## 1. Roles
| Quién | Qué hizo |
|---|---|
| **Yo (Rick)** | Definí el objetivo y el alcance en dos niveles (primero lo necesario, luego el plus), aporté el enunciado, contrasté la propuesta con otra IA, pedí seguridad, crecimiento, backups y documentación, fijé la restricción de costo (K8s y nube **opcionales**), pedí material para presentar y reporté fallos reales de mi entorno para depurar. Valido, pruebo, adapto y presento. |
| **Claude (IA)** | Propuso la arquitectura, escribió el código, los tests, los manifiestos y la documentación, y verificó parcialmente lo que su entorno permitía (ver sección 5). |

## 2. Bitácora de prompts (mis instrucciones, en orden)
Resumen fiel de lo que pedí en la conversación:

| # | Mi instrucción | Qué buscaba / decidí | Qué entregó la IA |
|---|---|---|---|
| 1 | Subí el PDF del reto: "vamos a dar solución... primero cubramos lo necesario y después el plus... vamos a brillar" | Alcance en dos niveles y repositorio Git | Diseño, proyecto Java/Spring, dominio y servicio con 18 tests, infraestructura y API |
| 2 | "Continuar" (dos veces) | Aprobar el avance de la construcción por partes | Capas siguientes del proyecto, README inicial |
| 3 | "Comparemos vs lo que me dejó Gemini" + plan de Gemini | **No adoptar una sola propuesta sin contrastarla** | Comparativa; recomendó mantener su diseño y sumar Testcontainers |
| 4 | "Sí, y no olvidemos la seguridad, el crecimiento transparente, backups y demás... generar la doc para presentarlo y el porqué de cada componente, y para mí tener el 100 % dominado" | Ampliar a seguridad, escalado, backups, documentación y dominio del código | API key, Testcontainers, Kubernetes, backups, CI, docs y guía de estudio |
| 5 | "Para poder probar... si necesitamos k8 o alguna nube con costo, dejarlos como opcionales... escribe dentro de la carpeta la solución... pasos para presentarlo... plus con control de poder realizarlo o no... uso de IA, tiempos, codificación y lo que yo haré" | **Restricción de costo** y control de lo avanzado | Makefile, componentes Kustomize opt-in, perfil de compose, presentación, guía paso a paso, este documento |
| 6 | "¿Dónde obtengo la key?" (dos veces) | Entender cómo usar la autenticación | Explicación de la API key y cómo generarla |
| 7 | "Dame un JSON para importar a Postman... para todas las APIs... demo visible para el team" | Demo visual para el equipo | Colección Postman (37 requests con tests) y 3 environments |
| 8 | Pegué el log de arranque: "Me genera unauthorized" | **Depuración con evidencia real de mi entorno** | Diagnóstico (key no coincidente), eliminación de un aviso innecesario, log de keys cargadas |
| 9 | "¿Se puede generar desde docker compose? ¿Cuál es la arquitectura?" | Generar la key sin fricción y entender el diseño | `make key` / `make show-key` y explicación de la arquitectura |
| 10 | "Dame un archivo detallado de todo... README más claro... apartado de IA con prompts, validaciones, testing" | Material claro para presentar | `PROYECTO-DETALLADO.md`, README reescrito, este documento, `make evidencia` |

## 3. Cómo dirigí a la IA (patrones que usé)
1. **Alcance por niveles:** "lo necesario primero, el plus después" → núcleo funcional antes de las capas avanzadas.
2. **Contraste:** pedir comparar contra el plan de otra IA antes de comprometerme.
3. **Pedir el porqué:** cada componente debía venir con su justificación y alternativa descartada.
4. **Restricción explícita:** costo y nube opcionales → todo lo avanzado es opt-in y controlable.
5. **Pedir material para defender:** presentación, guion, preguntas probables, ejercicios.
6. **Aportar evidencia real al depurar:** logs de mi propia ejecución en lugar de describir el problema.

## 4. Decisiones que tomé yo
| Decisión | Contexto | Resultado |
|---|---|---|
| Contrastar con otro plan antes de seguir | Había dos propuestas | Mantener la arquitectura propuesta y sumar Testcontainers; descartar mapear toda falla del proveedor a `REJECTED` |
| Incluir seguridad, crecimiento y backups | Pedí "lo demás" además del reto | Implementado; lo no implementable aquí quedó documentado |
| Kubernetes y nube **opcionales** | Costo y complejidad para evaluar | `make k8s-local` gratis con kind; nube real solo como ejemplo |
| Demo en Postman para el equipo | Presentación visible | Colección + environments por modo de ejecución |
| Lenguaje y stack (Java/Spring Boot) | La IA lo propuso; lo acepté | TODO-RICK (mi razón: p. ej. dominio previo del stack, coincide con el de Spin) |
| Postura sobre 201 en `REJECTED` y orden `PENDING` | Se desvía del enunciado | TODO-RICK (mi opinión tras revisarlo; plantearla como pregunta al equipo) |

## 5. Validación: qué se verificó y qué verifico yo

### 5.1 Verificado por la IA antes de entregarme el código
- Dominio y aplicación **compilados y ejecutados**: 18 tests en verde (arnés JDK, sin Spring).
- Sintaxis válida de YAML (compose, Kubernetes, CI), JSON (Postman) y scripts Bash; el `Makefile` se interpreta correctamente.
- La lógica de `make key` probada en una carpeta temporal (genera, no sobrescribe).
- El HTML de la presentación está bien formado.

### 5.2 **No** verificado por la IA (lo confirmo yo)
Compilación y ejecución de Spring/JPA/seguridad, adaptador HTTP, Resilience4j, tests de integración, Docker, Kubernetes y
Postman. Declararlo evita presentar como probado lo que no lo está.

### 5.3 Mi validación (evidencia que genero yo)
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

## 6. Testing: qué existe y qué cubre
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

## 7. Tiempos
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

## 8. Cómo se codificó (orden y por qué)
1. Dominio y aplicación sin framework (el riesgo de negocio) y verificados de forma aislada.
2. Infraestructura detrás de interfaces.
3. API y seguridad, que dependen de los códigos de error del dominio.
4. Pruebas de integración, incluida concurrencia sobre PostgreSQL real.
5. Operación (Docker, K8s, backups, CI) como capas opcionales sobre un núcleo que ya funcionaba.
6. Documentación y presentación al final, con las decisiones ya tomadas.

## 9. Riesgos de usar IA y mitigaciones
| Riesgo | Mitigación |
|---|---|
| Código que no compila o API inexistente | `make verify` y `make evidencia` en mi máquina; errores corregidos y documentados |
| Comprensión superficial | Ejercicios de modificación, simulacro de preguntas, explicar en voz alta |
| Decisiones plausibles pero mal justificadas | Alternativas descartadas y razones por escrito (`ARQUITECTURA.md`, `PROYECTO-DETALLADO.md` §8) |
| Presentar como probado lo no probado | Sección 5 separa lo verificado de lo pendiente, con evidencia |
| Sobre-ingeniería | Lo opcional está aislado y apagado por defecto |
| Seguridad (secretos, dependencias) | Sin secretos en el repo, `.env` ignorado, fail-safe sin API key, Dependabot |

## 10. Lo que hago yo antes de presentar
- [ ] Correr `make evidencia` y revisar que `verify.exitcode` sea `0` (TODO-RICK)
- [ ] Correr `make demo` y la colección de Postman, y entender cada escenario (TODO-RICK)
- [ ] Hacer el ejercicio de mutación y al menos 4 ejercicios de la guía de estudio (TODO-RICK)
- [ ] Completar los `TODO-RICK` de este documento y del README con datos reales (TODO-RICK)
- [ ] Probar los opcionales que voy a mostrar (cuáles: TODO-RICK)
- [ ] Ensayar la presentación en voz alta y las preguntas frecuentes (TODO-RICK)

## 11. Declaración (para el README)
Usar esta frase **solo después** de haber ejecutado y validado (sección 5.3). TODO-RICK: confirmar que es verdad y quitar esta nota.

> Usé Claude como asistente de ingeniería: yo definí el alcance y las restricciones (núcleo primero, plus opcional, sin
> costo de nube), contrasté su propuesta con el plan de otra IA y dirigí cada fase mediante prompts. La IA generó código,
> tests, manifiestos y documentación; yo ejecuté y validé el resultado (suite de tests, demo y colección Postman), documenté
> la evidencia en `evidencias/` y puedo explicar y defender cada decisión. Detalle en `docs/USO-DE-IA.md`.
