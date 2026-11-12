# T12 – Tolerancia a fallos y códigos HTTP

Regla general: **ninguna falla termina en un 500 ni en un resultado inventado.** Si falta una respuesta, el sistema o ofrece una alternativa honesta (aplicación pendiente) o responde 503/504 con `Retry-After`. Un 500 queda solo para errores de programación.

| Situación | Quién lo detecta | Código | Qué pasa con los datos |
|---|---|---|---|
| ms-tratamientos caído, conexión rechazada, 5xx o respuesta inválida **al registrar una aplicación** | ms-campos (`AplicacionService`) | **202** `PENDIENTE_DE_VALIDACION` | Se guarda pendiente, nunca aprobada sola; el agrónomo la reevalúa |
| ms-tratamientos no contesta a tiempo **al registrar una aplicación** | ms-campos | **202** (igual) | Igual. `evaluar()` no se reintenta (podría duplicar la evaluación) |
| ms-tratamientos caído **al planificar una campaña** | `FallosHandler` | **503** | No se crea la campaña |
| ms-tratamientos no contesta a tiempo **al planificar** o **al reevaluar** | `FallosHandler` | **504** | No se crea / la aplicación no cambia |
| ms-tratamientos caído al reevaluar | `FallosHandler` | **503** | La aplicación sigue pendiente |
| SoilGrids/Georef caídos al **crear un lote** | `LoteService` | **201** (lote `SIN_DATOS`) | El agrónomo carga el suelo o refresca |
| SoilGrids/Georef caídos o lentos en **`/suelo/refrescar`** | `FallosHandler` | **503** / **504** | El lote queda como estaba |
| Base de datos caída o sin conexiones | `FallosHandler` (ms-campos y ms-tratamientos) | **503** | — |
| Consulta o transacción que excede el tiempo | `FallosHandler` | **504** | — |
| Keycloak caído y el servicio **no** tiene las claves (JWKS) en memoria | `IdentidadNoDisponibleEntryPoint` | **503** (no 401) | — |
| Token ausente, vencido o con firma inválida | Spring Security | **401** | — |
| Token válido sin permiso / recurso ajeno | gateway y servicios | **403** | — |
| Dos usuarios modifican a la vez | `ConflictoHandler` | **409** | Gana el primero |
| Un microservicio caído (el pedido llega al gateway) | gateway (`ErroresGatewayHandler`) | **503** | — |
| Un microservicio no contesta en 30 s | gateway | **504** | — |
| Un microservicio corta la conexión a mitad de respuesta | gateway | **502** | — |

**503 vs 504:** 503 = el servicio está caído o respondió mal (volver a intentar más tarde); 504 = no contestó dentro del tiempo (puede estar lento: el pedido pudo ejecutarse igual).
`ApiExternaException.esTimeout()` marca el timeout (`SocketTimeoutException` / `TimeoutException`, también si vienen envueltas); una conexión rechazada **no** es timeout.

## Tiempos (presupuesto)
- ms-campos → ms-tratamientos: conexión 2 s, lectura 5 s, 1 reintento solo en consultas (GET). Planificar una campaña hace hasta 3 consultas de cultivo: peor caso ≈ 3 × 2 × 5 s.
- ms-campos → SoilGrids/Georef: conexión 3 s, lectura 10 s / 5 s, 1 reintento.
- Gateway → microservicios: conexión 3 s (`AGRO_GATEWAY_CONNECT_TIMEOUT`), respuesta 30 s (`AGRO_GATEWAY_RESPONSE_TIMEOUT`). **El tiempo de respuesta del gateway debe ser mayor que el peor caso de ms-campos**; si se achican los timeouts internos, se puede achicar este.

## Límites conocidos
- Si ms-tratamientos **guardó** la evaluación pero la respuesta no llegó (timeout de lectura), ms-campos deja la aplicación pendiente sin referencia a esa evaluación; la reevaluación crea una nueva. Queda una evaluación huérfana (inofensiva).
- No hay circuit breaker (Resilience4j) ni cola de reintentos: es un extra no comprometido. Con la caída sostenida de ms-tratamientos, cada pedido espera hasta el timeout antes de fallar.
- `agro-commons` sigue devolviendo 500 para rutas inexistentes o métodos incorrectos (pendiente de T11).
