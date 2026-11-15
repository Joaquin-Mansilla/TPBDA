# TPBDA · TPI Backend agro

| Carpeta | Contenido |
|---|---|
| [agro-commons](agro-commons/) | Librería compartida por los microservicios (contratos, errores, seguridad) |
| [docker](docker/) | Infraestructura local: PostgreSQL y Keycloak (ver [docker/README.md](docker/README.md)) |
| [ms-campos](ms-campos/) | Microservicio de establecimientos, lotes y campañas (puerto 8083) |
| [ms-tratamientos](ms-tratamientos/) | Microservicio de catálogo y evaluaciones (puerto 8082) |
| [gateway](gateway/) | Punto de entrada, enruta a los microservicios (puerto 8080) |

## Levantar todo

Requisitos: Docker Desktop abierto. Nada más (Java y Maven corren adentro de Docker).

```bash
cp .env.example .env        # solo la primera vez; cambiar las contraseñas
docker compose up -d --build
docker compose ps           # esperar a que los 5 digan healthy
```

En Windows, si el build falla al instante con `additional privileges requested: pass "--allow=fs.read=..."`
(pasa con Docker Compose v5), correrlo así: `BUILDX_BAKE_ENTITLEMENTS_FS=0 docker compose up -d --build`
(PowerShell: `$env:BUILDX_BAKE_ENTITLEMENTS_FS="0"; docker compose up -d --build`).

| Contenedor | Puerto publicado | Para qué |
|---|---|---|
| `agro-gateway` | **8080** | Única entrada a la API: `http://localhost:8080/api/v1/...` |
| `agro-keycloak` | **8081** | Tokens y consola de administración |
| `agro-ms-tratamientos` | 8082 (solo desarrollo, override) | Catálogo y evaluaciones · Swagger en `/swagger-ui.html` |
| `agro-ms-campos` | 8083 (solo desarrollo, override) | Establecimientos, lotes, campañas y aplicaciones · Swagger |
| `agro-postgres` | 5432 (solo desarrollo, override) | Base `agro` (esquemas `campos` y `tratamientos`) y base `keycloak` |

Comandos útiles:

| Para | Comando |
|---|---|
| Ver logs de un servicio | `docker compose logs -f ms-campos` |
| Recompilar después de cambiar código | `docker compose up -d --build` |
| Apagar sin perder datos | `docker compose stop` |
| Probar un servicio caído (T12) | `docker compose stop ms-tratamientos` y después `docker compose start ms-tratamientos` |
| Correr un servicio a mano con Maven | `docker compose stop ms-campos` y después `mvn spring-boot:run` en su carpeta |
| Empezar de cero (borra los datos) | `docker compose down -v` y `docker compose up -d --build` |

## Compilar y testear sin Docker

1. `agro-commons`: `cd agro-commons && mvn clean install`
2. En `ms-tratamientos`, `ms-campos` y `gateway`: `mvn clean test`

En Windows, si Maven da error de certificados (PKIX): `MAVEN_OPTS="-Djavax.net.ssl.trustStoreType=Windows-ROOT"`.
