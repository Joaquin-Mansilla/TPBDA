# Infraestructura local (T05)

| Contenedor | Imagen | Puerto en tu máquina | Para qué |
|---|---|---|---|
| agro-postgres | postgres:16 | 5432 (solo con el override) | Base `agro` con esquemas `campos` y `tratamientos`, y base `keycloak` |
| agro-keycloak | keycloak 26.0.7 | 8081 | Realm `agro` con roles, cliente y usuarios de prueba |

## Levantar

    cp .env.example .env        # y cambiar las contraseñas
    docker compose up -d
    docker compose ps           # los dos "healthy"

## Base de datos

| Esquema | Usuario | Servicio |
|---|---|---|
| campos | campos_user | ms-campos |
| tratamientos | tratamientos_user | ms-tratamientos |

Cada usuario es dueño de su esquema y no tiene permisos sobre el otro.

Configuración en `application.yml` (corriendo en la máquina; dentro de Docker el host es `postgres`):

    spring:
      datasource:
        url: jdbc:postgresql://localhost:5432/agro
        username: campos_user
        password: ${CAMPOS_DB_PASSWORD}
      jpa:
        properties:
          hibernate:
            default_schema: campos

ms-tratamientos es igual con `tratamientos_user` y `default_schema: tratamientos`.

## Keycloak

Consola: http://localhost:8081 (usuario y contraseña del `.env`), realm **agro**.
Cliente para pedir tokens: `agro-cliente` (público, password y Authorization Code + PKCE).

| Usuario | Contraseña | Rol | sub |
|---|---|---|---|
| productor1 | productor1 | PRODUCTOR | 11111111-1111-1111-1111-000000000001 |
| productor2 | productor2 | PRODUCTOR | 11111111-1111-1111-1111-000000000002 |
| operario1 | operario1 | OPERARIO | 22222222-2222-2222-2222-000000000001 |
| agronomo1 | agronomo1 | AGRONOMO | 33333333-3333-3333-3333-000000000001 |

Resource Server en los microservicios:

    spring:
      security:
        oauth2:
          resourceserver:
            jwt:
              issuer-uri: http://localhost:8081/realms/agro
              # dentro de Docker: http://keycloak:8080/realms/agro/protocol/openid-connect/certs
              jwk-set-uri: http://localhost:8081/realms/agro/protocol/openid-connect/certs

`KC_HOSTNAME` fija el issuer en `http://localhost:8081/realms/agro` para todos los tokens, aunque
el pedido llegue desde adentro de Docker. Los servicios validan ese issuer y bajan las claves por
la URL interna.

## Importante

- El script de la base y el realm se aplican **una sola vez**, con la base vacía. Si se cambian:
  `docker compose down -v` (borra TODOS los datos) y `docker compose up -d`.
- Si se cambia el realm desde la consola, exportarlo y reemplazar `realm-agro.json`:

      docker exec agro-keycloak /opt/keycloak/bin/kc.sh export --realm agro --users realm_file --file /tmp/realm-agro.json
      docker cp agro-keycloak:/tmp/realm-agro.json docker/keycloak/realm-agro.json
