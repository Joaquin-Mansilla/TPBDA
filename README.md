# agro-commons

Librería Maven compartida por `ms-campos` y `ms-tratamientos`. El Gateway **no** la usa (es reactivo).

## Instalar en el repositorio local (.m2)
    mvn clean install

## Usarla desde un microservicio (pom.xml)
    <dependency>
        <groupId>ar.edu.agro</groupId>
        <artifactId>agro-commons</artifactId>
        <version>1.0.0</version>
    </dependency>

## Qué contiene
- `contract`: DTOs que se intercambian los servicios (pedido y respuesta de evaluación, resumen de cultivo, enums).
- `security`: constantes de roles y conversor de `realm_access.roles` a `ROLE_*`.
- `error`: modelo de error, excepciones semánticas y `GlobalExceptionHandler` (se registra solo por autoconfiguración).

## Qué NO contiene (queda en cada servicio)
Entidades, repositorios, reglas de negocio y configuración de base de datos.

## En el servicio (Resource Server)
    http.oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(KeycloakJwtAuthenticationConverter.build())));
