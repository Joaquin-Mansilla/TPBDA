# ms-tratamientos

Microservicio del catálogo agronómico (cultivos, plagas, agroquímicos) y de la evaluación de
aplicaciones. Puerto **8082**.

- Base: `agro`, esquema `tratamientos`, usuario `tratamientos_user`.
- Seguridad: Resource Server; valida el JWT de Keycloak con el conversor de `agro-commons`.
- Swagger: http://localhost:8082/swagger-ui.html

## Correr en tu máquina

Requisitos: la infraestructura levantada (`docker compose up -d` en la raíz) y `agro-commons`
instalado (`mvn clean install` en `agro-commons/`).

Spring **no lee el `.env`**: la contraseña de la base se pasa como variable `DB_PASSWORD`
(el valor de `TRATAMIENTOS_DB_PASSWORD` de tu `.env`).

Git Bash:

    DB_PASSWORD=cambiar_tratamientos mvn spring-boot:run

PowerShell:

    $env:DB_PASSWORD="cambiar_tratamientos"; mvn spring-boot:run

IntelliJ: en la configuración de ejecución, *Environment variables* → `DB_PASSWORD=cambiar_tratamientos`.

## Variables

| Variable | Por defecto | Para qué |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/agro` | En Docker: `jdbc:postgresql://postgres:5432/agro` |
| `DB_USER` | `tratamientos_user` | Usuario de la base |
| `DB_PASSWORD` | (ninguno, obligatoria) | Contraseña de `tratamientos_user` |
| `JWT_ISSUER_URI` | `http://localhost:8081/realms/agro` | Tiene que coincidir con el `iss` del token |
| `JWT_JWK_SET_URI` | `http://localhost:8081/realms/agro/protocol/openid-connect/certs` | En Docker: `http://keycloak:8080/realms/agro/protocol/openid-connect/certs` |

`GET /api/v1/cultivos/ping` es **temporal**: devuelve el `sub` y los roles del token, para probar
el Gateway y la seguridad. Se borra cuando existan los endpoints reales.
