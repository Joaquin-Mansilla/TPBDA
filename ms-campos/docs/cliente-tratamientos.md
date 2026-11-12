# Cliente de ms-campos hacia ms-tratamientos (T10)

ms-campos le pide dos cosas a ms-tratamientos, **reenviando el token del usuario** que originó el pedido (no usa una credencial genérica de servicio):

| Proceso | Llamada | Para qué |
|---|---|---|
| Planificar campaña (F04) | `GET /api/v1/cultivos/{id}` | Familia, ventana de siembra y días de ciclo (C3, C4, cosecha estimada) |
| Registrar aplicación (F07) | `POST /api/v1/evaluaciones` | Decisión de las reglas T1 a T5, dosis total y costo |

La llamada va **directo** por la red interna (no pasa por el Gateway). Tratamientos igual valida el token como Resource Server.

## Configuración (application.yml de ms-campos)

Este bloque ya está en el `application.yml` de ms-campos (integrado). La URL se pisa con `TRATAMIENTOS_URL` (en Docker: `http://ms-tratamientos:8082`, ya previsto en el despliegue):

    agro:
      tratamientos:
        url-base: ${TRATAMIENTOS_URL:http://localhost:8082}
        timeout-conexion: 2s
        timeout-lectura: 5s
        reintentos-lectura: 1

## Clases (paquete `ar.edu.agro.campos.client`)

| Clase | Para qué |
|---|---|
| `TratamientosClient` | **Es lo que usa el resto de ms-campos.** `obtenerCultivo(id)` → `CultivoResumenResponse`; `evaluar(pedido)` → `EvaluacionResponse` (contratos de agro-commons) |
| `ReenvioTokenInterceptor` | Toma el JWT del `SecurityContext` y lo agrega como `Authorization: Bearer ...` |

Configuración en `ar.edu.agro.campos.config`: `TratamientosProperties` y `ClienteTratamientosConfig` (bean `tratamientosRestClient`). Reutiliza `ClienteApiExterna` y `LlamadaExternaInterceptor` de la T09. El controller temporal `TratamientosPingTemporalController` ya no existe: Tratamientos tiene sus endpoints reales.

## Qué hace cada falla

| Situación | Resultado | Qué debe hacer quien llama |
|---|---|---|
| Tratamientos no responde, timeout o 5xx | `ApiExternaException` (503 si llega al controller) | **Planificar campaña:** dejar que llegue como 503, no se crea la campaña. **Registrar aplicación:** guardarla `PENDIENTE_DE_VALIDACION` y responder 202 |
| El cultivo (planificar) o el cultivo, la plaga o el agroquímico (evaluar) no existen: 404 de Tratamientos | `RecursoNoEncontradoException` (404, con el mensaje de Tratamientos) | Dejarla llegar al controller: es un dato inválido del usuario y no se guarda nada, **no** una aplicación pendiente |
| 401, 403 o 400 de Tratamientos | `ApiExternaException`, con el detalle en el log | Igual que "no responde". Un 401 suele ser un problema de configuración (issuer del token); un 400 es un pedido de Campos que no respeta el contrato (se registra en el log como ERROR) |
| Respuesta con datos inválidos (cultivo con 0 días de ciclo, evaluación sin resultado, REPROGRAMAR sin fecha) | `ApiExternaException` | Igual que "no responde": no se inventa un veredicto |
| No hay JWT en el contexto de seguridad | `IllegalStateException` (500) | Es un error de programación: ver la nota de abajo |

Uso al registrar una aplicación (esquema):

    EvaluacionResponse decision;
    try {
        decision = tratamientosClient.evaluar(pedido);
    } catch (ApiExternaException e) {
        // guardar la aplicación como PENDIENTE_DE_VALIDACION y devolver 202
        return ...;
    }
    switch (decision.resultado()) {
        case VIABLE -> ... // guardar REGISTRADA con decision.id(), 201
        case REPROGRAMAR -> ... // 422 con decision.fechaSugerida()
        case NO_RECOMENDADO -> ... // 422 con decision.motivos()
        case REVISION_MANUAL -> ... // guardar PENDIENTE_DE_VALIDACION, 202
    }

## Decisiones de diseño

1. **`evaluar()` no se reintenta nunca.** Tratamientos guarda cada evaluación: si hay un timeout y se repite el pedido, puede quedar duplicada. Si no responde, la aplicación queda pendiente y el AGRONOMO la reevalúa (`POST /aplicaciones/{id}/reevaluacion`). Las consultas de cultivo (GET) sí se reintentan una vez, porque no tienen efectos.
2. **El token se toma del hilo del pedido.** Funciona porque el `SecurityContext` de Spring es por hilo. **No usar el cliente desde `@Async`, `@Scheduled` ni hilos propios** (ahí no hay token y lanza `IllegalStateException`). Si más adelante hace falta una llamada sin usuario (por ejemplo, un aviso programado de carencias), habría que usar credenciales de servicio (client credentials).
3. **Un 401 o 403 de Tratamientos no se muestra al usuario como 401 o 403.** Campos ya validó su token; si Tratamientos lo rechaza es un problema entre servicios, y el usuario recibe 503 o 202 según el proceso.
4. **El token nunca se escribe en el log.** El interceptor de log solo registra método, URL, estado y tiempo.

## Cómo verificarlo de punta a punta

Ver `docs/flujo-aplicaciones.md`: las pruebas V4 a V7 del cuestionario recorren este cliente (planificar con Tratamientos caído da 503; registrar con Tratamientos caído deja la aplicación pendiente; al volver, la reevaluación la registra). El token reenviado queda registrado en Tratamientos como quien solicitó la evaluación (`solicitada_por_sub`, visible en `GET /api/v1/evaluaciones/{id}`).

## Tests

    mvn test    # no usa la red: simula a ms-tratamientos

Cubren el reenvío del token (y que se usa el del usuario actual), cada tipo de falla, que `evaluar()` no se reintente, y la validación de las respuestas.
