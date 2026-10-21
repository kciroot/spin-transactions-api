# Prompts de revisión (para ejecutar con la IA y registrar los resultados)

Cómo usarlos: pega el prompt en la conversación del proyecto (o en una nueva con el repositorio adjunto), revisa la respuesta,
**decide qué aplicar y qué descartar**, y registra fecha, hallazgos y tu decisión en `docs/USO-DE-IA.md` (sección 5).
Una revisión solo cuenta cuando la ejecutaste.

## 1. Seguridad
> Actúa como revisor de seguridad (AppSec) de una API financiera en Java/Spring Boot. Revisa el repositorio adjunto con foco en:
> autenticación y autorización, validación de entrada, manejo de secretos, errores y fugas de información, dependencias,
> contenedor y manifiestos de Kubernetes. Para cada hallazgo indica severidad (alta/media/baja), archivo y línea, escenario de
> ataque y corrección concreta. Separa lo que está implementado de lo que corresponde al gateway/plataforma. No inventes
> vulnerabilidades: si algo no se puede verificar leyendo el código, dilo.

Evidencia a guardar: hallazgos, cuáles corregiste y por qué descartaste los demás.

## 2. Escalabilidad
> Actúa como ingeniero de rendimiento. Analiza el flujo de `POST /transactions` asumiendo 5 millones de transacciones al día,
> pico de 10 veces y proveedor de 300 ms. Identifica cuellos de botella (hilos, pool de conexiones, índices, bloqueo,
> crecimiento de la tabla), calcula la capacidad con supuestos explícitos y propone cambios ordenados por impacto/costo.
> Indica qué se puede validar con una prueba de carga y cómo diseñarla.

Evidencia: tabla de cuellos de botella y la decisión sobre cada uno.

## 3. Calidad de código
> Actúa como revisor senior de código Java. Revisa el repositorio buscando: clases con más de una responsabilidad, nombres que no
> comunican intención, duplicación, dependencias que violan la arquitectura hexagonal (dominio/aplicación no deben importar
> Spring), manejo de errores inconsistente y deuda técnica. Prioriza por impacto y propone refactors pequeños y seguros.
> Respeta el principio de pragmatismo: no sugieras abstracciones que hoy no se justifiquen.

Evidencia: lista de refactors aceptados y rechazados con razón.

## 4. Brechas de testing
> Actúa como ingeniero de calidad. Revisa los tests del repositorio e identifica: casos de negocio sin cubrir, aserciones débiles,
> tests que pasarían aunque el código estuviera roto, dependencias del orden de ejecución y condiciones de carrera sin probar.
> Propón los 5 tests faltantes de mayor valor y una mutación manual para comprobar que cada test crítico puede fallar.

Evidencia: tests agregados y resultado de la mutación.

## 5. Preparación de la defensa
> Actúa como entrevistador técnico de Spin. Hazme 15 preguntas difíciles sobre este proyecto (diseño, concurrencia, resiliencia,
> seguridad, escalamiento, uso de IA), una por una, y evalúa mi respuesta señalando qué faltó o qué es incorrecto.

Evidencia: preguntas en las que fallaste y qué reforzaste.
