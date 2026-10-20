# Seguridad

Enfoque: controles reales y verificables en el servicio, y claridad sobre lo que corresponde al entorno
(gateway, red, gestor de secretos). Lo implementado está marcado con ✅; lo que se espera de la plataforma, con 🏗️;
lo pendiente, con ⏳.

## Modelo de amenazas resumido

| Amenaza | Control | Estado | Dónde |
|---|---|---|---|
| Acceso no autorizado a la API | API key por header `X-API-Key`, **deny-by-default** (todo requiere key salvo health y docs) | ✅ | `infrastructure/security/SecurityConfig` |
| Timing attack sobre la key | Comparación en tiempo constante sobre digests SHA-256 (`MessageDigest.isEqual`) | ✅ | `ApiKeyAuthFilter` |
| Arranque inseguro por olvido de configuración | La app **se niega a arrancar** sin al menos una API key | ✅ | `SecurityConfig` |
| Rotación de credenciales | Varias keys válidas a la vez (`SECURITY_API_KEYS=nueva,vieja`), se retira la vieja luego | ✅ | `SecurityProperties` |
| Secretos en el repositorio | Todo por variables de entorno; `secret.example.yaml` solo como ejemplo; en K8s vía Secret/External Secrets | ✅ | `application.yml`, `deploy/k8s` |
| Inyección SQL | Consultas parametrizadas (JPA/Criteria), nunca concatenación | ✅ | `TransactionSpecifications` |
| Entrada maliciosa o malformada | Bean Validation (tipos, longitudes, 2 decimales), enums estrictos, `limit` ≤ 100, `Idempotency-Key` ≤ 128 | ✅ | `CreateTransactionRequest`, `TransactionController` |
| Fuga de información en errores | Errores RFC 7807 con `code` estable; los 500 no devuelven stack traces ni detalles internos | ✅ | `ApiExceptionHandler` |
| Doble ejecución de dinero | Idempotencia con `UNIQUE` + id enviado al proveedor | ✅ | `TransactionService`, migración V1 |
| Redondeo / pérdida de precisión | `BigDecimal` + `NUMERIC(19,2)`, se rechazan >2 decimales | ✅ | `TransactionRules`, V1 |
| Abuso / DoS de bajo nivel | Timeouts hacia el proveedor, circuit breaker, pool acotado, paginación acotada | ✅ | `ProviderConfig`, `application.yml` |
| Contenedor comprometido | Imagen con usuario no root; en K8s: `runAsNonRoot`, FS de solo lectura, sin capabilities, `seccomp` | ✅ | `Dockerfile`, `deploy/k8s/deployment.yaml` |
| Movimiento lateral en el cluster | NetworkPolicy: solo el ingress controller llega a la API | ✅ | `deploy/k8s/networkpolicy.yaml` |
| Dependencias vulnerables | Dependabot (Maven, Docker, Actions) + build en CI | ✅ | `.github/` |
| Documentación expuesta en producción | `SPRINGDOC_ENABLED=false` en el ConfigMap de producción | ✅ | `deploy/k8s/configmap.yaml` |
| Interceptación en tránsito | TLS terminado en el ingress/gateway; entre pods, mTLS con service mesh si se requiere | 🏗️ | plataforma |
| Autenticación de clientes a escala (JWT/OAuth2/mTLS, scopes por cliente) | La API key es el mínimo viable servicio-a-servicio; en producción lo natural es OAuth2 client-credentials en el API gateway | 🏗️/⏳ | gateway |
| Rate limiting por cliente/cuenta | En el gateway (más eficiente y centralizado que en cada réplica) | 🏗️ | gateway |
| Cifrado en reposo y de backups | Disco/BD gestionada con cifrado (KMS); backups cifrados | 🏗️ | plataforma |
| Credenciales de BD con mínimo privilegio | Usuario de la app solo con DML (sin DDL); migraciones con otro usuario | ⏳ | BD |
| Auditoría de quién hizo qué | Registrar identidad del cliente por transacción (columna `client_id`) y logs de acceso | ⏳ | ver roadmap |
| Datos personales | El servicio solo guarda `accountId`, monto y descripción libre; **no** loguea el body. Evitar PII en `description` | ✅/⏳ | — |

## Decisión: ¿por qué API key y no JWT?

El reto describe un servicio interno de ejecución de transacciones. Una API key estática, comparada en tiempo
constante, con rotación y fail-safe, es la solución mínima honesta para servicio-a-servicio. Implementar emisión y
validación de JWT dentro del servicio duplicaría lo que ya hace el IdP/gateway de la empresa y añadiría superficie
de ataque. La integración futura es directa: cambiar `ApiKeyAuthFilter` por `oauth2ResourceServer()` sin tocar el
dominio, porque la seguridad vive solo en `infrastructure/security`.

## Checklist antes de producción

- [ ] `SECURITY_API_KEYS` generadas con alta entropía (≥ 32 bytes aleatorios) y guardadas en el gestor de secretos.
- [ ] `SPRINGDOC_ENABLED=false`.
- [ ] TLS en el ingress; política de red aplicada.
- [ ] Usuario de BD de mínimo privilegio; backups cifrados.
- [ ] Rate limiting y autenticación fuerte en el gateway.
