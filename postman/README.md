# Colección de pruebas integrales (Postman / Newman)

| Archivo | Qué es |
|---|---|
| `TPBDA.postman_collection.json` | La colección (Postman v2.1): 92 requests y 207 asserts |
| `TPBDA-local.postman_environment.json` | Entorno "TPBDA local": `baseUrl`, `keycloakUrl` y `gatewayUrl` |

Todas las pruebas pasan por el **gateway** (`http://localhost:8080`), igual que un cliente real.

## 1. Importar en Postman

File → Import → elegir los dos archivos. Arriba a la derecha, seleccionar el entorno **TPBDA local**.

## 2. Antes de correrla

La carpeta **06** usa los datos de prueba que se cargan solos con la base vacía. Para que pase completa,
la base tiene que estar **recién creada**:

```bash
docker compose down -v                                   # ⚠️ borra los datos locales
BUILDX_BAKE_ENTITLEMENTS_FS=0 docker compose up -d --build
docker compose ps                                        # esperar a que los 5 digan healthy
```

Las demás carpetas crean sus propios datos o no modifican nada: se pueden correr las veces que se quiera.

## 3. Demos para mostrar (carpetas 01 y 02)

La carpeta **01 · Demo** es el flujo completo para presentar, de arriba hacia abajo: correr primero la **00** (tokens)
y después tocar **Send** en cada request de la 01, en orden. Cada paso guarda los ids que necesita el siguiente, y crea
sus propios datos: se puede repetir las veces que se quiera sin borrar la base.

Los pasos con **200/201** son el camino feliz. Los pasos con **403/422**, intercalados, muestran lo que el sistema
rechaza en ese momento del flujo: no guardan nada, así que el camino feliz sigue igual.

| Paso | Qué muestra | Validaciones intercaladas |
|---|---|---|
| 01 a 07 | El productor crea el establecimiento (con operario1 asignado) y un lote de 100 ha; el agrónomo carga el suelo | 02 lote fuera del rectángulo (C2) · 03 lote más grande que el establecimiento (C2) · 05 otro productor no ve el lote (C6) · 06 el operario no carga suelo (rol) |
| 08 a 12 | **Dos cultivos en el mismo lote**: 60 ha de soja y 40 ha de maíz al mismo tiempo | 08 trigo fuera de su ventana (C4) · 09 campaña más grande que el lote (C2) · 12 una tercera campaña ya no entra (C1) |
| 13 a 16 | Se registra la siembra de las dos (hoy) | 13 siembra con fecha futura (C8) · 15 sembrar otra vez (C5) |
| 17 y 18 | **Lluvia cargada a mano**: el operario carga 6,4 mm de lluvia de hoy en el lote | 17 lluvia con fecha futura (C8) |
| 19 a 22 | **¿Toca regar?** Recién sembrada da `TOCA_REGAR`: con la lluvia le faltan 18,6 mm para el primer riego; el operario riega solo esos mm (`mmFaltantes`), queda `AL_DIA` y el próximo riego vence en 7 días | 20 el agrónomo no registra riegos (rol) |
| 23 a 27 | **Alerta**: monitoreo de oruga al 35 % → alerta pendiente → aplicación de Clorpirifos (VIABLE) → alerta atendida | 25 dosis fuera de rango: NO_RECOMENDADO (T2), no se guarda y la alerta sigue pendiente |
| 28 a 30 | **Historial del lote** con todo lo anterior (la soja sigue sembrada: la cosecha se muestra en la carpeta 02) | 28 cosechar en la fecha estimada, que todavía no llegó (C8) · 29 cosechar hoy, 150 días antes de la estimada (C9) |

Los 403 por rol (06 y 20) los corta el gateway y llegan sin cuerpo; el 403 por pertenencia (05) lo responde ms-campos.

La lluvia se carga a mano (paso 18), así que el demo da siempre lo mismo: en el paso 19 la soja recién sembrada
juntó 6,4 mm y le faltan 18,6 para el primer riego (`TOCA_REGAR`). El campo `mensaje` lo explica y también sale en la
consola de Postman. Para probar otros casos, cambiá los mm del paso 18 (0 si no llovió; 25 o más cuenta como riego).
La siembra es **hoy**: de octubre a diciembre cae en la ventana de la soja y del maíz. Fuera de esos meses, planificar da 422 C4.

### Carpeta 02 · Demo de cosecha

La regla **C9** permite cosechar desde **10 días antes** de la cosecha estimada, el mismo día o después. En la carpeta
01 la soja se siembra hoy (su cosecha es dentro de 150 días), así que la cosecha se muestra en esta carpeta, con
**trigo sembrado hace meses** (ciclo de 180 días) en tres lotes:

| Lote | Sembrado hace | Cosecha estimada | Cosechar hoy |
|---|---|---|---|
| A | 170 días | dentro de 10 días | 10 días antes: se puede (ayer, 11 días antes, da 422 C9) |
| B | 180 días | **hoy** | el mismo día: se puede |
| C | 185 días | hace 5 días | después: se puede (cosechar otra vez da 422 C5) |

Termina con el historial del lote B: 320.000 kg en 100 ha, 3.200 kg/ha. Como la siembra tiene que caer en la ventana
del trigo (mayo a julio), esta carpeta funciona si hoy está **entre el 2 de noviembre y el 18 de enero**.

## 4. Correrla entera

- **Postman:** botón **Run** sobre la colección, en el orden en que está.
- **Newman** (desde la raíz del repo):

  ```bash
  npx --yes newman run postman/TPBDA.postman_collection.json -e postman/TPBDA-local.postman_environment.json
  ```

Los tokens se piden en la carpeta 00 y duran una hora. Si vencieron (o no se corrió la 00), el script de la
colección los pide de nuevo a Keycloak antes de mandar el request.

## 5. Qué prueba cada carpeta

| Carpeta | Qué prueba | Tarea |
|---|---|---|
| 00 · Tokens | Pide un token para productor1, productor2, operario1 y agronomo1 | T05 |
| 01 · Demo de punta a punta | Ver el punto 3: el camino feliz con validaciones intercaladas (C1, C2, C4, C5, C6, C8, C9, T2 y roles); dos cultivos por lote (C1), riego con lluvia cargada a mano (R1), alertas de plagas (M1), aplicación e historial del lote | Mejoras |
| 02 · Demo de cosecha | Ver el punto 3: trigo sembrado hace 170, 180 y 185 días; cosecha 10 días antes de la estimada, el mismo día y 5 días después (C9), rechazo de 11 días antes (C9) y de cosechar otra vez (C5); rendimiento e historial | Mejoras |
| 03 · Seguridad | Health sin token, 401 sin token, 403 por rol (operario planifica, productor crea cultivo) y por pertenencia (productor2 en el establecimiento de productor1) | T08 |
| 04 · Catálogo | Listados de cultivos, plagas y agroquímicos; el agrónomo crea un cultivo | T07 |
| 05 · Recorrido completo | Establecimiento con rectángulo → lote fuera de los límites y lote que no entra (C2) → lote (SoilGrids/Georef o SIN_DATOS) → suelo a mano → planificar soja hoy → C1, C2, C4 → siembra real fuera de tolerancia (C4) → registrar la siembra → sembrar otra vez (C5) → monitoreo → aplicación antes de la siembra (C8) → aplicación → consulta de la evaluación → cosechar el mismo día de la siembra (C9) | T07, T09, T10, T11, T16 |
| 06 · Reglas con datos de prueba | VIABLE (201), REPROGRAMAR por T3, NO_RECOMENDADO por T5, T1 y T2, pendiente por L1 (202), rotación C3, superficie tratada C2 | T07, T10, T11 |
| 07 · Agrónomo resuelve pendientes | Bandeja, autorizar sin justificación (400), operario no autoriza (403), autorizar (200), A1 (422) | T08, T11 |
| 08 · Validaciones y errores | Cuerpo inválido (400 con `detalles`), campaña inexistente (404), agroquímico inexistente (404) | T07, T12 |

**Fechas relativas a hoy (T16).** Un script de la colección define, antes de cada request, `{{hoy}}`,
`{{hoyMenos1}}`, `{{hoyMenos10}}`, `{{hoyMenos60}}`, `{{hoyMas3}}`, `{{hoyMas7}}`, `{{hoyMas10}}`, `{{hoyMas150}}`, `{{hoyMas200}}` y `{{hoyMas365}}`
(formato `AAAA-MM-DD`, con la fecha local de la máquina). La carpeta 05 planifica y siembra **soja hoy**, y la ventana
de siembra de la soja es **de octubre a diciembre**: si se corre de enero a septiembre, planificar y sembrar dan 422 C4.
En ese caso hay que cambiar a un cultivo cuya ventana incluya el mes actual (maíz, `cultivoId` 2, septiembre a
diciembre; trigo, `cultivoId` 3, mayo a julio) y ajustar las fechas de C1 y C4 y la cosecha esperada
(`hoyMas150` es el ciclo de la soja).

La consulta de una evaluación (`GET /evaluaciones/{id}`, del catálogo) está al final de la carpeta 05:
necesita el id de una evaluación, que recién existe después de registrar una aplicación.

### Si se corre dos veces sin borrar la base

Con la base recién creada pasaban los 98 asserts de las carpetas 00 y 03 a 08 (comprobado con Newman antes de
agregar los demos). En una segunda corrida sin borrar la base fallan 11, todos en las carpetas 06 y 07:

| Request | Qué pasa en la segunda corrida |
|---|---|
| 06 · `201 · Aplicación viable` | Da **422 REPROGRAMAR**: la aplicación del 20/11 ya existe y no pasaron los 14 días (T3) |
| 06 · `202 · Pendiente por falta de datos del lote (L1)` | Da **422 REPROGRAMAR**: la del lote 2 autorizada en la corrida anterior también cuenta para T3, y T3 se evalúa antes que L1 |
| 07 · Bandeja, autorizar y A1 | Fallan en cadena: no hay una pendiente nueva para resolver (el id queda vacío y la API responde 400) |

Las carpetas 00 a 05 y 08 pasan igual en cualquier corrida.

## 6. Tolerancia a fallos (T12, a mano)

No están en la colección porque hay que apagar contenedores:

- `docker compose stop ms-tratamientos`
  - registrar una aplicación válida → **202** `PENDIENTE_DE_VALIDACION`
  - planificar una campaña → **503** (o **504** si se prueba enseguida de pararlo)
  - `docker compose start ms-tratamientos`
- `docker compose stop ms-campos`
  - cualquier endpoint de campos por el gateway → **503/504**
  - `docker compose start ms-campos`

## 7. Swagger

Con el override activo (es el comportamiento por defecto de `docker compose up`):

- ms-tratamientos: http://localhost:8082/swagger-ui.html
- ms-campos: http://localhost:8083/swagger-ui.html

Botón **Authorize** → pegar el token (sin la palabra `Bearer`). El token se saca con cualquier request de
la carpeta 00 (campo `access_token` de la respuesta).
