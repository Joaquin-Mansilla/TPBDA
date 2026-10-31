# gateway

Spring Cloud Gateway (WebFlux). Único punto de entrada al backend. Puerto **8080**.
No usa `agro-commons` (es reactivo).

## Rutas

| Prefijo | Destino |
|---|---|
| `/api/v1/establecimientos/**`, `/api/v1/lotes/**`, `/api/v1/campanias/**`, `/api/v1/aplicaciones/**` | ms-campos (`CAMPOS_URL`, por defecto `http://localhost:8083`) |
| `/api/v1/cultivos/**`, `/api/v1/plagas/**`, `/api/v1/agroquimicos/**`, `/api/v1/evaluaciones/**` | ms-tratamientos (`TRATAMIENTOS_URL`, por defecto `http://localhost:8082`) |

## Seguridad

Exige un JWT válido de Keycloak en todo salvo `/actuator/health`. La autorización por rol, ruta y
método se agrega en la tarea de seguridad (T08).

## Correr en tu máquina

    mvn spring-boot:run

En Docker se pisan `CAMPOS_URL=http://ms-campos:8083`, `TRATAMIENTOS_URL=http://ms-tratamientos:8082`
y `JWT_JWK_SET_URI=http://keycloak:8080/realms/agro/protocol/openid-connect/certs`.
