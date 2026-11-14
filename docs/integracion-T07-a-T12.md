# Integración T07 + T09 + T10 + T11

Contenido: `ms-campos/` y `ms-tratamientos/` completos (reemplazar las carpetas). **No incluye** `agro-commons` (se usa el del repo, sin cambios) ni `gateway` (el de T07/T08 sirve tal cual).

## Qué se hizo

1. **T07 adaptada al `agro-commons` real.** El código de T07 se escribió contra un commons reconstruido. Diferencias resueltas (sin tocar el commons):
   - `EvaluacionResponse.id` (no `evaluacionId`); `EvaluacionRequest` con `superficieTratadaHa` y otro orden de campos.
   - `AplicacionPreviaDto` solo trae la `fecha`: ms-campos manda únicamente las previas **del mismo producto** y ms-tratamientos usa la última fecha (T3).
   - `SueloDto.carbonoOrganicoGKg` y `UbicacionDto` con `municipio`.
   - `ReglaNegocioException` lleva los motivos como texto: helper local `ErroresDeRegla` (en cada servicio) que arma `"C5: detalle"`.
2. **T09 y T10 reemplazan los clientes provisorios de T07** (SoilGrids, Georef y Tratamientos). `LoteService` usa `DatosExternosService`; `CampaniaService` y `AplicacionService` usan `TratamientosClient`, que reenvía el token. El interruptor `agro.externas.habilitado` (variable `AGRO_EXTERNOS_HABILITADO`, perfil `h2`) se conservó.
3. **T11** (flujo de aplicaciones): ver `ms-campos/docs/flujo-aplicaciones.md`.

## Correcciones encontradas al integrar

| Qué | Antes | Ahora |
|---|---|---|
| Sub de `operario1` en los datos de ejemplo | `11111111-...-000000000003` (no existe en el realm: el operario no habría visto nada) | `22222222-2222-2222-2222-000000000001` |
| Clase de textura | 2 criterios distintos (T07: arcilla ≥35 primero y arena ≥60; T09: arena ≥50 primero y arcilla ≥35) | uno solo, el de la propuesta, configurable (`agro.externas.textura`) |
| `ConflictoHandler` (409) frente al handler genérico de commons | podía no usarse nunca (el de commons atrapa `Exception` y devuelve 500) | `@Order(HIGHEST_PRECEDENCE)` |
| `GET /aplicaciones?estado=XXX` | 500 | 400 |
| Evaluar con un agroquímico, plaga o cultivo inexistente | (T10) quedaba como aplicación pendiente | 404 y no se guarda nada |
| Ruta de Georef | `/georef/api/ubicacion` (T07) | `/georef/api/v2.0/ubicacion` (la de la propuesta) |

## Cambios de T11 sobre el flujo de T07

- Se guardan `dosisTotal`, `envases` y `costo` de la última evaluación en `aplicacion` (3 columnas nuevas, `ddl-auto: update` las crea) para que `GET /aplicaciones/{id}` los muestre (F08).
- `reevaluar` guarda siempre la referencia a la última evaluación, también cuando sigue pendiente.
- `autorizar` guarda la justificación sin espacios sobrantes.
- Tests: `AplicacionServiceTest` (32 casos), `LoteServiceTest`, `ConflictoHandlerTest` y `EvaluacionServiceTest` (ms-tratamientos).

## Tests que hay que borrar o saber que cambiaron

- Se borró `ClientesExternosTest` (T07): probaba los clientes provisorios, ahora cubiertos por `SoilGridsClientTest` y `GeorefClientTest`.
- Se quitó de `ReglasCampaniaTest` el test de textura (ahora en `ClasificadorTexturaTest`).

## Pendientes y decisiones abiertas

- **No se compiló ni se corrió nada** (sin acceso a Maven Central). Hay que correr `mvn clean install` en `agro-commons` y después `mvn test` en cada servicio. Lo más probable: algún import o detalle de versión.
- Correr **una vez** `PROBAR_APIS_REALES=true mvn test -Dtest=ApisExternasRealesTest` en ms-campos (confirma los nombres de campo de SoilGrids y Georef).
- Resolver una pendiente cuya campaña ya no está SEMBRADA (cosechada o perdida) hoy se permite. Si el grupo prefiere bloquearlo (C5), es un `if` en `reevaluar` y `autorizar`.
- `LoteService.crear` consulta las APIs externas dentro de una transacción (puede tardar hasta unos 20 s con reintento). Funciona, pero retiene una conexión de la base durante ese tiempo.
- `agro-commons`: sigue pendiente que `GlobalExceptionHandler` devuelva 404/405/400 (hoy 500) para rutas inexistentes, método incorrecto o parámetro faltante. ms-campos ya cubre el parámetro con tipo inválido.
- Si el repo ya tenía cambios posteriores a T07 en `agro-commons`, hay que revisar que `EvaluacionRequest`/`EvaluacionResponse` sigan como en la versión del 31/10.


---

# T12 – Tolerancia a fallos (agregado)

Ver `ms-campos/docs/tolerancia-a-fallos.md` (tabla situación → código HTTP, tiempos y límites).

## Qué cambió
| Archivo | Cambio |
|---|---|
| ms-campos `client/ApiExternaException`, `client/ClienteApiExterna` | Distingue **timeout** de caída (`esTimeout()`), también con la causa envuelta |
| ms-campos `config/FallosHandler` (nuevo) | `ApiExternaException`/`ServicioNoDisponibleException` → 503 o 504; base de datos caída → 503, timeout de base → 504; todos con `Retry-After` |
| ms-tratamientos `config/FallosHandler` (nuevo) | Base de datos caída → 503, timeout → 504 |
| ms-campos y ms-tratamientos `config/IdentidadNoDisponibleEntryPoint` (nuevo) + `SecurityConfig` | Keycloak sin respuesta → 503 en vez de 401 |
| gateway `ErroresGatewayHandler`, `TimeoutsGatewayConfig`, `IdentidadNoDisponibleEntryPoint` (nuevos) + `SecurityConfig` | 503 / 504 / 502 con el formato común de error; timeouts de conexión (3 s) y respuesta (30 s) |
| Tests nuevos | ms-campos `FallosHandlerTest`, `ToleranciaAFallosClienteTest`; ms-tratamientos `FallosHandlerTest`; gateway `ToleranciaAFallosTest` |

## Antes de usarlo
- **Igual que antes: no se compiló ni se corrió nada** (sin acceso a Maven Central). Correr `mvn test` en ms-campos, ms-tratamientos y gateway.
- Los handlers nuevos usan `ErrorResponse(Instant, int, String, String, String, List<String>)` de `agro-commons` y los accesores `status()`, `path()`, `message()` tal como los usa `ConflictoHandler`.
- El gateway del zip **no incluye** su `application.yml`: copiar solo los archivos nuevos y el `SecurityConfig` modificado. Si ya definen timeouts por `application.yml`, el bean `TimeoutsGatewayConfig` los pisa.
- Demostración con Docker: `docker compose stop ms-tratamientos` y probar `POST /campanias/{id}/aplicaciones` (202), `POST /lotes/{id}/campanias` (503); `docker compose stop postgres` y cualquier GET (503); `docker compose stop ms-campos` y cualquier pedido por el gateway (503).
