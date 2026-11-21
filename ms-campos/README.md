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
| `POST /api/v1/campanias/{id}/cosecha` | PRODUCTOR | `{"fecha"}` → COSECHADA |
| `POST /api/v1/campanias/{id}/perdida` | PRODUCTOR | `{"fecha", "motivo"}` → PERDIDA |
| `POST /api/v1/campanias/{id}/monitoreos` | PRODUCTOR, OPERARIO | Monitoreo de plaga |
| `POST /api/v1/campanias/{id}/aplicaciones` | PRODUCTOR, OPERARIO | Proceso principal (ver `docs/flujo-aplicaciones.md`) |
| `GET /api/v1/aplicaciones`, `/{id}`; `POST .../autorizacion`, `.../reevaluacion` | ver flujo | Bandeja y resolución de pendientes |

El estado de la campaña **no se manda a mano**: cambia con siembra, cosecha y pérdida. `EstadoCampania` conoce sus
transiciones (PLANIFICADA → SEMBRADA o PERDIDA; SEMBRADA → COSECHADA o PERDIDA).

## Reglas de fechas y superficie (T16)

| Regla | Qué valida |
|---|---|
| C2 | El lote cae dentro del rectángulo del establecimiento y la suma de los lotes no supera su superficie |
| C4 | La siembra real cae en la ventana del cultivo y no se aparta más de `agro.campanias.tolerancia-siembra-dias` (45) de la planificada |
| C5 | Transiciones de estado permitidas; la pérdida exige motivo |
| C8 | Siembra, cosecha, pérdida y monitoreo no pueden ser futuros; la cosecha (y la pérdida de una sembrada) no puede ser anterior a la siembra real; el monitoreo va de la siembra real a hoy; la aplicación va de la siembra real a la cosecha estimada (sí puede ser futura) |

Planificar, crear un lote, refrescar el suelo y sembrar llaman a otro servicio **fuera de la transacción**; después,
una transacción corta (`TransactionTemplate`) valida contra la base y guarda.

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
