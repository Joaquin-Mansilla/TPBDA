# Flujo completo de evaluación de aplicaciones (T11)

Código: `AplicacionService` (ms-campos) y `EvaluacionService` (ms-tratamientos). Tests: `AplicacionServiceTest` y `EvaluacionServiceTest`.

## Registrar: `POST /api/v1/campanias/{id}/aplicaciones` (PRODUCTOR dueño u OPERARIO asignado)

ms-campos valida C6 (pertenencia, 403), C5 (campaña SEMBRADA) y C2 (superficie tratada ≤ sembrada); si alguna falla, 422 con los motivos y **no llama a Tratamientos**. Después arma el pedido (cultivo, cosecha estimada, suelo y ubicación del lote, y las aplicaciones previas del **mismo producto** en el lote, solo las REGISTRADAS o AUTORIZADAS) y llama a Tratamientos con el token del usuario.

| Respuesta de Tratamientos | Estado de la aplicación | HTTP |
|---|---|---|
| VIABLE | REGISTRADA (con id de evaluación, dosis total, envases y costo) | 201 |
| REPROGRAMAR (solo falla T3) | no se guarda; se devuelve la fecha sugerida | 422 |
| NO_RECOMENDADO (falla otra regla) | no se guarda; se devuelven los motivos | 422 |
| REVISION_MANUAL (faltan suelo o ubicación, L1) | PENDIENTE_DE_VALIDACION | 202 |
| No responde (timeout, 5xx, respuesta inválida) | PENDIENTE_DE_VALIDACION, **nunca aprobada sola** | 202 |
| Agroquímico, plaga o cultivo inexistente | no se guarda | 404 |

## Resolver una pendiente (solo AGRONOMO)

- **Bandeja:** `GET /api/v1/aplicaciones?estado=PENDIENTE_DE_VALIDACION`. El agrónomo ve todas; el productor, las de sus establecimientos; el operario no lista (403). Un estado inexistente da 400.
- **Reevaluar:** `POST /api/v1/aplicaciones/{id}/reevaluacion` vuelve a consultar a Tratamientos (útil cuando volvió a estar disponible o cuando se completó el suelo del lote).

| Resultado | La aplicación | HTTP |
|---|---|---|
| VIABLE | pasa a REGISTRADA | 200 |
| REVISION_MANUAL | sigue pendiente | 202 |
| REPROGRAMAR o NO_RECOMENDADO | sigue pendiente (se puede autorizar a mano); en el cuerpo van los motivos | 422 |
| Tratamientos no responde | no cambia | 503 |

  En todos los casos se guarda la referencia a la **última evaluación** (id, dosis total, envases y costo).
- **Autorizar:** `POST /api/v1/aplicaciones/{id}/autorizacion` con `{"justificacion": "..."}`. Sin justificación (o en blanco): 400. Pasa a AUTORIZADA_MANUALMENTE y registra qué agrónomo la autorizó.
- **Regla A1:** solo se reevalúa o autoriza una aplicación pendiente; si no lo está (por ejemplo, ya autorizada), 422.
- **Concurrencia:** si dos agrónomos resuelven la misma pendiente a la vez, el segundo recibe **409** (columna `version`); si Tratamientos tarda y otro agrónomo autoriza mientras tanto, la reevaluación también recibe 409 y no pisa la autorización.

## Consultar: `GET /api/v1/aplicaciones/{id}` (PRODUCTOR dueño, OPERARIO asignado, AGRONOMO)

Devuelve la aplicación con su `evaluacionId`, `dosisTotal`, `envases` y `costo` (se guardan en ms-campos al registrar o reevaluar, así la consulta no depende de que Tratamientos esté disponible; el detalle completo de la decisión está en `GET /api/v1/evaluaciones/{id}` de Tratamientos, solo AGRONOMO). Esto agrega tres columnas a `aplicacion` respecto del DER del cuestionario.

## Estados

    (se registra) ──VIABLE──────────────▶ REGISTRADA
         │
         ├─REPROGRAMAR / NO_RECOMENDADO ─▶ (no se guarda)
         │
         └─REVISION_MANUAL / sin respuesta ▶ PENDIENTE_DE_VALIDACION ──reevaluar VIABLE──▶ REGISTRADA
                                                    │
                                                    └──autorizar con justificación──▶ AUTORIZADA_MANUALMENTE

## Prueba a mano (criterios V4 a V7 del cuestionario)

Con Postgres y Keycloak (`docker compose up -d`) y los tres servicios corriendo (`mvn spring-boot:run`; sin Docker: `-Dspring-boot.run.profiles=h2` en los dos servicios). Datos de ejemplo: campaña 1 SEMBRADA en el lote 1 (suelo OK) y campaña 2 SEMBRADA en el lote 2 (SIN_DATOS); producto 1 Clorpirifos con una aplicación del 2026-11-05 en el lote 1; plaga 1 Oruga militar.

    OP=$(curl -s -X POST http://localhost:8081/realms/agro/protocol/openid-connect/token \
      -d grant_type=password -d client_id=agro-cliente -d username=operario1 -d password=operario1 | jq -r .access_token)
    AG=$(curl -s -X POST http://localhost:8081/realms/agro/protocol/openid-connect/token \
      -d grant_type=password -d client_id=agro-cliente -d username=agronomo1 -d password=agronomo1 | jq -r .access_token)

    # V6: lote SIN_DATOS -> 202 PENDIENTE_DE_VALIDACION (guardá el aplicacionId)
    curl -s -X POST http://localhost:8080/api/v1/campanias/2/aplicaciones -H "Authorization: Bearer $OP" \
      -H "Content-Type: application/json" \
      -d '{"agroquimicoId":1,"plagaId":1,"dosisPorHa":1.0,"superficieTratadaHa":30,"fecha":"2026-11-20"}'

    # el agrónomo la ve en su bandeja, la autoriza sin justificación (400) y con justificación (200)
    curl -s -H "Authorization: Bearer $AG" "http://localhost:8080/api/v1/aplicaciones?estado=PENDIENTE_DE_VALIDACION"
    curl -s -X POST http://localhost:8080/api/v1/aplicaciones/ID/autorizacion -H "Authorization: Bearer $AG" \
      -H "Content-Type: application/json" -d '{}'
    curl -s -X POST http://localhost:8080/api/v1/aplicaciones/ID/autorizacion -H "Authorization: Bearer $AG" \
      -H "Content-Type: application/json" -d '{"justificacion":"Revisé el lote en campo"}'

    # V5 (a): mismo producto antes del intervalo mínimo en el lote 1 -> 422 REPROGRAMAR con fecha sugerida
    curl -s -X POST http://localhost:8080/api/v1/campanias/1/aplicaciones -H "Authorization: Bearer $OP" \
      -H "Content-Type: application/json" \
      -d '{"agroquimicoId":1,"plagaId":1,"dosisPorHa":1.0,"superficieTratadaHa":30,"fecha":"2026-11-10"}'

    # V7: detené ms-tratamientos, registrá una aplicación válida (202), levantalo y reevaluá (200 REGISTRADA)
    curl -s -X POST http://localhost:8080/api/v1/aplicaciones/ID/reevaluacion -H "Authorization: Bearer $AG"

Cambiá `ID` por el `aplicacionId` devuelto. (Las contraseñas de los usuarios de prueba son iguales al usuario, como en el realm del repo.)
