# Uso de inteligencia artificial: qué se hizo, cómo y con qué criterio

El enunciado permite y alienta usar IA. Este documento explica **cómo la usé, qué verifiqué yo y por qué las
decisiones de ingeniería son mías**. Los campos marcados con `TODO-RICK` son datos reales míos (tiempos, acciones
y fechas) que deben completarse antes de entregar; `make pre-entrega` falla mientras queden pendientes.

## 1. Postura
La IA fue un acelerador de ejecución, no quien decide. Yo definí el problema, evalué alternativas, acepté o
rechacé propuestas y soy responsable de cada línea. Para garantizarlo: leí y puedo explicar el código (ejercicios
y simulacros documentados abajo), lo compilé y probé yo, y modifiqué lo que no me convenció.

## 2. Herramientas
| Herramienta | Para qué | Qué NO le delegué |
|---|---|---|
| Claude (chat, claude.ai) | Discutir el diseño, generar el esqueleto y el código inicial, tests, manifiestos de despliegue y documentación | La decisión del lenguaje/stack, aceptar o no el flujo `PENDING`, el alcance de lo opcional, la verificación final |
| Gemini | Plan inicial alternativo que usé como **contraste** (ver 3) | — |

## 3. Cómo contrasté propuestas (criterio propio)
Pedí un plan a otra IA y lo comparé con la propuesta de Claude. Lo que adopté y lo que descarté:

| Tema | Plan alternativo | Propuesta de Claude | Mi decisión |
|---|---|---|---|
| Lenguaje | Go, Node o Java sin decidir | Java 17 + Spring Boot | Java: es el que mejor domino, el reto pide "tu mejor código"; TODO-RICK (confirma o ajusta tu razón) |
| Fallo del proveedor | Todo fallo = `REJECTED` | Separar rechazo (definitivo) de caída (`FAILED`, resultado desconocido) | Adopté la separación: mezclarlos oculta un riesgo real en dinero |
| Orden de persistencia | Persistir al final | `PENDING` primero, luego estado final | Adopté `PENDING` primero; lo explico como desvío consciente del enunciado |
| Pruebas con BD real | Testcontainers como plus | H2 en modo PostgreSQL | Combiné: H2 en la suite rápida + Testcontainers contra PostgreSQL real |

## 4. Reparto del trabajo por fase
La columna "sin IA" es una **estimación orientativa** de esfuerzo manual para un ingeniero con experiencia (no es
un dato medido). La columna "real" es mi tiempo efectivo y debe ser verdadera.

| Fase | Qué hizo la IA | Qué hice yo | Sin IA (est.) | Real |
|---|---|---|---|---|
| Análisis del enunciado y diseño | Propuso arquitectura, estados, flujo e idempotencia | Evaluar alternativas, decidir y cuestionar (secc. 3) | 3–4 h | TODO-RICK |
| Dominio + servicio + tests unitarios | Código inicial y casos de prueba | Revisión línea por línea, ajuste de reglas, ejercicios de modificación | 4–6 h | TODO-RICK |
| Persistencia, adaptador HTTP, resiliencia | Código de infraestructura | Verificar contra la documentación de Spring/Resilience4j, ejecutar y depurar | 5–7 h | TODO-RICK |
| API, errores, seguridad | Controller, manejador de errores RFC 7807, filtro de API key | Revisión de seguridad y de códigos HTTP | 3–4 h | TODO-RICK |
| Tests de integración (H2 y Testcontainers) | Escribió los tests | Correrlos, corregir fallos de entorno, comprobar que fallan cuando deben (mutación: quitar `UNIQUE`) | 3–4 h | TODO-RICK |
| Docker, Kubernetes, backups, CI | Manifiestos y scripts modulares | Probar `make up`; opcionales: probar los que decida mostrar | 4–6 h | TODO-RICK |
| Documentación y presentación | Borradores de docs y presentación | Reescribir con mis palabras, ensayar, preparar respuestas | 4–6 h | TODO-RICK |
| **Total** | | | **26–37 h** | **TODO-RICK** |

**Por qué el tiempo real es menor (o distinto) que el manual:** la IA elimina la escritura repetitiva (DTOs,
configuración, boilerplate de tests y manifiestos) y acelera la exploración de alternativas. No elimina la revisión,
la depuración ni la comprensión; ese tiempo lo invertí yo. TODO-RICK (una frase con tu experiencia real: dónde
tardaste más, qué tuviste que corregir).

## 5. Cómo se construyó (orden y por qué)
1. **Dominio y aplicación primero, sin framework.** Es donde vive el riesgo de negocio. Se compilaron y probaron de
   forma aislada (18 tests) antes de agregar infraestructura.
2. **Infraestructura después** (JPA, HTTP, resiliencia), detrás de interfaces, para poder cambiarla sin tocar reglas.
3. **API y seguridad** al final de la lógica, porque dependen de los códigos de error del dominio.
4. **Pruebas de integración**, incluida concurrencia sobre PostgreSQL real.
5. **Operación** (Docker, K8s, backups, CI) como capas opcionales sobre un núcleo que ya funcionaba.
6. **Documentación** al final, con las decisiones ya tomadas.

Ese orden reduce el riesgo: cada capa se apoya en la anterior ya verificada.

## 6. Cómo verifiqué el código generado
Una IA puede producir código plausible pero incorrecto. Mis controles:
- Compilación y ejecución de toda la suite (`make verify`) en mi máquina: TODO-RICK (fecha y resultado).
- **Los tests deben poder fallar:** quité la restricción `UNIQUE` y comprobé que el test de concurrencia falla;
  TODO-RICK (confirma que lo hiciste).
- Ejecución real de la demo (`make demo`) contra la aplicación levantada: TODO-RICK.
- Lectura línea por línea con la guía de estudio y los ejercicios de modificación (cambiar una regla, agregar un
  filtro, etc.): TODO-RICK (cuáles hiciste).
- Revisión de dependencias y versiones; Dependabot activo; sin secretos en el repositorio.
- Comparación de decisiones contra fuentes oficiales (documentación de Spring, Resilience4j, PostgreSQL) cuando
  tuve dudas: TODO-RICK (qué consulté).

## 7. Riesgos de usar IA y mitigaciones
| Riesgo | Mitigación |
|---|---|
| Código que no compila o API inexistente | Compilar y correr los tests yo mismo; los errores se corrigen y se documentan |
| Comprensión superficial ("no lo escribí yo") | Ejercicios de modificación, simulacro de preguntas, explicación en voz alta |
| Decisiones plausibles pero mal justificadas | Registrar alternativas descartadas y su razón (`docs/ARQUITECTURA.md`) |
| Sobre-ingeniería | Lo opcional está separado y apagado por defecto; el núcleo cubre exactamente el enunciado |
| Seguridad (secretos, dependencias) | Sin secretos en el repo, configuración por entorno, fail-safe sin API key, Dependabot |

## 8. Lo que hice yo (checklist que debo completar y poder demostrar)
- [ ] Levanté el proyecto y corrí `make verify` con resultado en verde (TODO-RICK)
- [ ] Corrí `make demo` y entendí cada escenario (TODO-RICK)
- [ ] Hice al menos 4 ejercicios de modificación de la guía (TODO-RICK)
- [ ] Ajusté el README y estos documentos con mis palabras y datos reales (TODO-RICK)
- [ ] Probé los opcionales que decidí mostrar (cuáles: TODO-RICK)
- [ ] Ensayé la presentación en voz alta y las preguntas frecuentes (TODO-RICK)

## 9. Declaración (para el README)
> Usé Claude como asistente de ingeniería para discutir el diseño, generar código inicial, pruebas, manifiestos de
> despliegue y documentación, y contrasté su propuesta con un plan alternativo de otra IA, adoptando y descartando
> partes con criterio propio. Compilé y probé todo el código, ejecuté la suite y la demo, y puedo explicar y defender
> cada decisión. Detalle de fases, tiempos y verificación en `docs/USO-DE-IA.md`.
