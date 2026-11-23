# ms-campos

Microservicio de establecimientos, lotes, campañas, monitoreos y aplicaciones. Puerto **8083**.

- Base: `agro`, esquema `campos`, usuario `campos_user`.
- Seguridad: Resource Server; valida el JWT de Keycloak con el conversor de `agro-commons`.
- Swagger: http://localhost:8083/swagger-ui.html

## Endpoints

| Método y ruta | Rol | Qué hace |
|---|---|---|
| `POST /api/v1/establecimientos` | PRODUCTOR | Crea un establecimiento con su **rectángulo** de coordenadas (`latitudMin/Max`, `longitudMin/Max`); se calculan sus hectáreas |
| `GET /api/v1/establecimientos`, `/{id}` | todos (C6) | Con `superficieHa` y `superficieLibreHa` |
| `POST /api/v1/establecimientos/{id}/lotes` | PRODUCTOR | Crea un lote (C2: dentro del rectángulo y en la superficie libre; suelo y ubicación por SoilGrids/Georef, L1) |
| `GET /api/v1/lotes/{id}` | todos (C6) | Lote con suelo y ubicación |
| `PATCH /api/v1/lotes/{id}/suelo`, `POST /api/v1/lotes/{id}/suelo/refrescar` | AGRONOMO | Suelo a mano o desde las APIs |
| `POST /api/v1/lotes/{id}/campanias` | PRODUCTOR | Planifica una campaña (C1 a C4) |
| `GET /api/v1/campanias/{id}` | todos (C6) | Campaña con fechas planificada y reales |
| `POST /api/v1/campanias/{id}/siembra` | PRODUCTOR | `{"fecha"}`: registra la siembra real → SEMBRADA, recalcula la cosecha estimada |
| `POST /api/v1/campanias/{id}/cosecha` | PRODUCTOR | `{"fecha", "kgCosechados"}` → COSECHADA; con los kg (opcionales) devuelve `rendimientoKgHa` |
| `POST /api/v1/campanias/{id}/perdida` | PRODUCTOR | `{"fecha", "motivo"}` → PERDIDA |
| `POST /api/v1/campanias/{id}/monitoreos` | PRODUCTOR, OPERARIO | Monitoreo de plaga; responde `alerta: true` si la incidencia llega al umbral (M1) |
| `GET /api/v1/campanias/{id}/alertas?pendientes=true` | todos (C6) | Monitoreos sobre el umbral y si ya se aplicó contra esa plaga (M1) |
| `POST /api/v1/campanias/{id}/riegos` | PRODUCTOR, OPERARIO | `{"fecha", "laminaMm"}`: registra un riego (C5: campaña sembrada; C8: de la siembra a hoy) |
| `GET /api/v1/campanias/{id}/riegos` | todos (C6) | Riegos de la campaña |
| `POST /api/v1/lotes/{id}/lluvias` | PRODUCTOR, OPERARIO | `{"fecha", "mm"}`: carga a mano la lluvia de un día en el lote (0 si no llovió; C8: no futura) |
| `GET /api/v1/lotes/{id}/lluvias` | todos (C6) | Lluvias cargadas en el lote |
| `GET /api/v1/campanias/{id}/riego` | todos (C6) | ¿Toca regar? `AL_DIA`, `TOCA_REGAR`, `ATRASADO`, `NO_REQUIERE` o `NO_SEMBRADA` (R1) |
| `GET /api/v1/lotes/{id}/historial` | todos (C6) | Campañas del lote con monitoreos, alertas, aplicaciones, riegos y rendimiento promedio por cultivo |
| `POST /api/v1/campanias/{id}/aplicaciones` | PRODUCTOR, OPERARIO | Proceso principal (ver `docs/flujo-aplicaciones.md`) |
| `GET /api/v1/aplicaciones`, `/{id}`; `POST .../autorizacion`, `.../reevaluacion` | ver flujo | Bandeja y resolución de pendientes |

El estado de la campaña **no se manda a mano**: cambia con siembra, cosecha y pérdida. `EstadoCampania` conoce sus
transiciones (PLANIFICADA → SEMBRADA o PERDIDA; SEMBRADA → COSECHADA o PERDIDA).

## Reglas de fechas y superficie (T16)

| Regla | Qué valida |
|---|---|
| C1 | Un lote puede tener **varias campañas al mismo tiempo** (por ejemplo 60 ha de soja y 40 ha de maíz) si son de **cultivos distintos** y entre todas las que se superponen en fechas no superan la superficie del lote. Se suman todas las superpuestas aunque entre ellas no se pisen: es la cuenta conservadora. Se controla al planificar y al sembrar (la siembra real mueve la cosecha estimada) |
| C2 | El lote cae dentro del rectángulo del establecimiento y la suma de los lotes no supera su superficie |
| C4 | La siembra real cae en la ventana del cultivo y no se aparta más de `agro.campanias.tolerancia-siembra-dias` (45) de la planificada |
| C5 | Transiciones de estado permitidas; la pérdida exige motivo |
| C8 | Siembra, cosecha, pérdida y monitoreo no pueden ser futuros; la cosecha (y la pérdida de una sembrada) no puede ser anterior a la siembra real; el monitoreo va de la siembra real a hoy; la aplicación va de la siembra real a la cosecha estimada (sí puede ser futura) |
| C9 | La cosecha se puede registrar desde `agro.campanias.anticipo-cosecha-dias` (10) días antes de la cosecha estimada, el mismo día o después (el cultivo tiene que estar maduro); antes, 422 con la fecha desde la que se puede |

Planificar, crear un lote, refrescar el suelo y sembrar llaman a otro servicio **fuera de la transacción**; después,
una transacción corta (`TransactionTemplate`) valida contra la base y guarda.

## Riego, alertas y rendimiento

**R1 · ¿Toca regar?** Cada cultivo del catálogo tiene `diasEntreRiegos` y `mmPorRiego` (soja: 25 mm cada 7 días;
maíz: 30 mm cada 5; el trigo va a secano y da `NO_REQUIERE`). `GET /campanias/{id}/riego` recorre los días desde la
siembra real hasta hoy sumando los riegos de la campaña y la **lluvia cargada a mano en el lote** (`POST /lotes/{id}/lluvias`).
Cada vez que lo sumado llega a `mmPorRiego`, ese día cuenta como "agua completa" (`origenUltimaAgua`: `RIEGO` o
`LLUVIA`) y se empieza a sumar de nuevo.

**Riego de asiento:** al sembrar, el cultivo ya necesita su primer riego completo. Mientras no junte `mmPorRiego` mm
entre riego y lluvia (`origenUltimaAgua: SIEMBRA`), no está al día: el riego vence el mismo día de la siembra.
Después, se cuenta desde la última agua completa:

| Situación | Estado |
|---|---|
| Sin el primer riego, hasta `agro.riego.tolerancia-dias` (2) días desde la siembra | `TOCA_REGAR` |
| Sin el primer riego, más días | `ATRASADO` |
| Menos de `diasEntreRiegos` días desde la última agua completa | `AL_DIA` |
| Hasta `diasEntreRiegos` + la tolerancia | `TOCA_REGAR` |
| Más | `ATRASADO` |

Por ejemplo, una soja sembrada hoy con 6,4 mm de lluvia da `TOCA_REGAR`: le faltan 18,6 mm para completar el primer
riego. Si se riegan esos 18,6 mm, pasa a `AL_DIA` y el próximo riego vence en 7 días. El campo `mensaje` explica cada caso
y dice cuántos mm juntó desde la última agua completa; `mmFaltantes` dice cuántos hay que regar para completar el riego.

El estado no se guarda: se calcula en cada consulta. Si ms-tratamientos no responde, 503 (sin la frecuencia del
cultivo no hay cálculo).

**Lluvia cargada a mano.** La lluvia no se consulta a ninguna API externa: la carga el productor o el operario con
`POST /lotes/{id}/lluvias` (`{"fecha", "mm"}`; sin fecha es hoy; no puede ser futura, C8), por ejemplo lo que marcó el
pluviómetro. Es del **lote**, no de la campaña, porque llueve sobre todo el lote: cuenta para todas sus campañas (en el
demo, la soja y el maíz). 0 mm deja registrado que no llovió, y un día sin carga cuenta como 0. Si se carga dos veces
el mismo día, se suman. `GET /lotes/{id}/lluvias` las lista.

**M1 · Alertas de plagas.** Un monitoreo con incidencia mayor o igual a `agro.monitoreos.umbral-alerta-pct` (20 %) es
una alerta. Queda **atendida** cuando hay una aplicación hecha (`REGISTRADA` o `AUTORIZADA_MANUALMENTE`) contra la misma
plaga en la campaña, con fecha igual o posterior al monitoreo.

**Rendimiento.** Al cosechar se pueden informar los `kgCosechados`; la campaña devuelve `rendimientoKgHa` (kg /
superficie sembrada). El historial del lote trae el promedio por cultivo, ponderado por hectárea.

**Quién hizo cada cosa.** Los usuarios se identifican por el `sub` del token, que nunca cambia y es lo que usa la
regla C6. Para que se lea quién hizo cada cosa, además se guarda un nombre para mostrar armado con `preferred_username`
y `name` del token, por ejemplo `operario1 (Oscar Operario)`: `productorNombre` en el establecimiento,
`registradoPorNombre` en riegos y monitoreos, y `registradaPorNombre` y `autorizadaPorNombre` en las aplicaciones. Es una
copia del momento en que se registró: si el usuario cambia su nombre en Keycloak, lo ya guardado no cambia. Los datos
personales siguen viviendo en Keycloak.

## Correr en tu máquina

Con Docker (recomendado): `docker compose up -d --build` en la raíz levanta este servicio junto con los demás.
Para correrlo a mano con Maven, primero pará su contenedor: `docker compose stop ms-campos`.

Requisitos: la infraestructura levantada (`docker compose up -d` en la raíz) y `agro-commons`
instalado (`mvn clean install` en `agro-commons/`).

Spring **no lee el `.env`**: la contraseña de la base se pasa como variable `DB_PASSWORD`
(el valor de `CAMPOS_DB_PASSWORD` de tu `.env`).

Git Bash:

    DB_PASSWORD=cambiar_campos mvn spring-boot:run

PowerShell:

    $env:DB_PASSWORD="cambiar_campos"; mvn spring-boot:run

IntelliJ: en la configuración de ejecución, *Environment variables* → `DB_PASSWORD=cambiar_campos`.

## Variables

| Variable | Por defecto | Para qué |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/agro` | En Docker: `jdbc:postgresql://postgres:5432/agro` |
| `DB_USER` | `campos_user` | Usuario de la base |
| `DB_PASSWORD` | (ninguno, obligatoria) | Contraseña de `campos_user` |
| `JWT_ISSUER_URI` | `http://localhost:8081/realms/agro` | Tiene que coincidir con el `iss` del token |
| `JWT_JWK_SET_URI` | `http://localhost:8081/realms/agro/protocol/openid-connect/certs` | En Docker: `http://keycloak:8080/realms/agro/protocol/openid-connect/certs` |

Para probar el Gateway y la seguridad: `GET /api/v1/establecimientos` (con un token de Keycloak).
