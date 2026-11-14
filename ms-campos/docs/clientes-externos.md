# Clientes de SoilGrids y Georef (T09)

ms-campos consulta dos APIs públicas **una sola vez por lote**, al crearlo (o al refrescarlo), y guarda el resultado en el lote. Las reglas de Tratamientos no dependen de que estas APIs respondan en ese momento.

| API | Qué aporta | Regla que depende de ella |
|---|---|---|
| SoilGrids (ISRIC) | % de arcilla, arena y limo, pH y carbono orgánico del punto | T2: dosis máxima según clase de textura |
| Georef (datos.gob.ar) | provincia, departamento y municipio del punto | T5: restricción territorial |

## Configuración (application.yml de ms-campos)

Este bloque ya está en el `application.yml` de ms-campos (integrado). Las URL base se pueden pisar con las variables de entorno `SOILGRIDS_URL` y `GEOREF_URL` (ya previstas en el despliegue):

    agro:
      externas:
        habilitado: ${AGRO_EXTERNOS_HABILITADO:true}   # false = sin Internet: los lotes quedan SIN_DATOS
        soilgrids:
          url-base: ${SOILGRIDS_URL:https://rest.isric.org/soilgrids/v2.0}
          profundidad: 0-5cm
          timeout-conexion: 3s
          timeout-lectura: 10s
          reintentos: 1
        georef:
          url-base: ${GEOREF_URL:https://apis.datos.gob.ar/georef/api/v2.0}
          timeout-conexion: 3s
          timeout-lectura: 5s
          reintentos: 1
        textura:
          umbral-arena-pct: 50
          umbral-arcilla-pct: 35

Si falta el bloque, ms-campos no arranca y el error indica qué propiedad falta. Con el perfil `h2` (sin Docker) `habilitado` es `false`.

## Clases (paquete `ar.edu.agro.campos.client`)

| Clase | Para qué |
|---|---|
| `DatosExternosService` | **Es lo que usa `LoteService`.** `consultar(lat, lon)` devuelve un `DatosExternosLote(suelo, ubicacion)` |
| `SoilGridsClient` | `consultarSuelo(lat, lon)` → `SueloDto` de agro-commons |
| `GeorefClient` | `consultarUbicacion(lat, lon)` → `UbicacionDto` de agro-commons |
| `ClasificadorTextura` | ARENOSO / ARCILLOSO / FRANCO a partir de los porcentajes (arena desde 50, arcilla desde 35). Lo usa también el `PATCH /lotes/{id}/suelo` manual |
| `ApiExternaException` | Falla de una API. Extiende `ServicioNoDisponibleException` de commons: llegando a un controller se traduce a **503** |
| `ClienteApiExterna` | Reintento y traducción de errores (base de los dos clientes) |
| `LlamadaExternaInterceptor` | Registra cada llamada saliente con estado y tiempo |

Configuración en `ar.edu.agro.campos.config`: `ApisExternasProperties` y `ClientesExternosConfig` (los beans `soilGridsRestClient` y `georefRestClient`). Si se agrega otro `RestClient` (por ejemplo el de Tratamientos), inyectarlo con `@Qualifier` porque ahora hay más de uno.

## Cómo se usa

`LoteService` ya está conectado: `POST /establecimientos/{id}/lotes` y `POST /lotes/{id}/suelo/refrescar` usan `DatosExternosService`. `consultar()` no lanza excepciones por fallas de las APIs (el dato viene en `null` y el lote queda SIN_DATOS); si no responde ninguna API, el refresco da 503.

## Cómo se comporta cuando algo falla

| Situación | Resultado |
|---|---|
| Timeout, conexión caída o error 5xx | 1 reintento; si sigue fallando, `ApiExternaException` |
| Error 4xx (incluye 429, límite de SoilGrids) | Sin reintento; `ApiExternaException` |
| JSON que no se puede leer, capa faltante, valor `null` (agua, zona urbana), porcentaje fuera de 0 a 100, pH fuera de 0 a 14 | Se trata como falla; no se guarda nada inventado y se registra en el log |
| Georef sin provincia (punto fuera de Argentina) | Se trata como falla |

## Decisiones a confirmar con el equipo

1. **Profundidad `0-5cm`**: es la de la propuesta (y la de su curl de prueba). Para agronomía suele interesar más la capa superior de 0 a 30 cm; cambiarlo es una propiedad, pero hay que usar una profundidad que SoilGrids ofrezca (`0-5cm`, `5-15cm`, `15-30cm`, ...).
2. **Timeout de lectura de SoilGrids: 10 s y 1 reintento.** SoilGrids es lento; en el peor caso crear un lote tarda unos 20 s. Si es mucho para la demo, bajar a 6 s.
3. **Límite de 5 consultas por minuto de SoilGrids:** no hay un limitador propio. Si se supera, SoilGrids responde 429, el lote queda SIN_DATOS y el AGRONOMO puede refrescar después. Tenerlo presente al crear lotes de prueba en seguida uno tras otro.

## Tests

    mvn test                                                      # no usa la red (respuestas simuladas)
    PROBAR_APIS_REALES=true mvn test -Dtest=ApisExternasRealesTest  # una vez, contra las APIs reales

Los tests con respuestas simuladas usan JSON con la forma de SoilGrids v2.0 y Georef v2.0 tal como están documentadas. **Hay que correr una vez la prueba real** (o comparar con los JSON de los curl de T01) para confirmar que los nombres de campo coinciden con lo que devuelven hoy las APIs.
