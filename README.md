# TPBDA · TPI Backend agro

| Carpeta | Contenido |
|---|---|
| [agro-commons](agro-commons/) | Librería compartida por los microservicios (contratos, errores, seguridad) |
| [docker](docker/) | Infraestructura local: PostgreSQL y Keycloak (ver [docker/README.md](docker/README.md)) |
| [ms-campos](ms-campos/) | Microservicio de establecimientos, lotes y campañas (puerto 8083) |
| [ms-tratamientos](ms-tratamientos/) | Microservicio de catálogo y evaluaciones (puerto 8082) |
| [gateway](gateway/) | Punto de entrada, enruta a los microservicios (puerto 8080) |

## Orden de build

1. `agro-commons`: `cd agro-commons && mvn clean install`
2. `ms-tratamientos`, `ms-campos` y `gateway`: en cada carpeta, `mvn clean package`
3. `docker compose up -d` desde la raíz (antes: `cp .env.example .env`)
