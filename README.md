# TPBDA · TPI Backend agro

| Carpeta | Contenido |
|---|---|
| [agro-commons](agro-commons/) | Librería compartida por los microservicios (contratos, errores, seguridad) |
| [docker](docker/) | Infraestructura local: PostgreSQL y Keycloak (ver [docker/README.md](docker/README.md)) |

## Orden de build

1. `agro-commons`: `cd agro-commons && mvn clean install`
2. Los microservicios (cuando se sumen), cada uno con `mvn clean install`
3. `docker compose up -d` desde la raíz (antes: `cp .env.example .env`)
