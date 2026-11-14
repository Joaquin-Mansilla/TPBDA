# gateway

Spring Cloud Gateway (WebFlux). Único punto de entrada al backend. Puerto **8080**.
No usa `agro-commons` (es reactivo).

## Rutas

| Prefijo | Destino |
|---|---|
| `/api/v1/establecimientos/**`, `/api/v1/lotes/**`, `/api/v1/campanias/**`, `/api/v1/aplicaciones/**` | ms-campos (`CAMPOS_URL`, por defecto `http://localhost:8083`) |
| `/api/v1/cultivos/**`, `/api/v1/plagas/**`, `/api/v1/agroquimicos/**`, `/api/v1/evaluaciones/**` | ms-tratamientos (`TRATAMIENTOS_URL`, por defecto `http://localhost:8082`) |

## Seguridad (T08)

El gateway valida el JWT de Keycloak y autoriza por **método, ruta y rol** (`SecurityConfig`).
Los roles salen de `realm_access.roles` y se convierten en `ROLE_*` con `KeycloakJwtConverter`
(copia reactiva del conversor de `agro-commons`, que el gateway no puede usar).

- Sin token o con token inválido: **401**. Con token válido pero sin permiso para ese método y ruta: **403**.
- Libre sin token: solo `/actuator/health`.
- Las reglas se evalúan en orden y gana la primera que coincide. **Lo que no está listado se rechaza (403)**:
  un endpoint nuevo queda cerrado hasta que se lo agrega en `SecurityConfig`.
- El gateway decide qué *rol* llama a qué endpoint. La **pertenencia (C6: un productor solo ve lo suyo)**
  no se resuelve acá: la aplica cada microservicio con `@PreAuthorize` sobre el `sub` del token.

| Operación | PRODUCTOR | OPERARIO | AGRONOMO |
|---|:-:|:-:|:-:|
| GET de campos y catálogo (`/establecimientos`, `/lotes`, `/campanias`, `/aplicaciones`, `/cultivos`, `/plagas`, `/agroquimicos`) | sí | sí | sí |
| GET `/evaluaciones/{id}` | no | no | sí |
| POST `/evaluaciones` (lo usa ms-campos con el token del usuario) | sí | sí | sí |
| Establecimientos y campañas: POST, PUT, PATCH, DELETE (incluye `POST /establecimientos/{id}/lotes`, `POST /lotes/{id}/campanias`, `PATCH /campanias/{id}/estado`) | sí | no | no |
| `PATCH /lotes/{id}/suelo`, `POST /lotes/{id}/suelo/refrescar` | no | no | sí |
| Monitoreos (`/campanias/{id}/monitoreos`): POST | sí | sí | no |
| Monitoreos: PUT, PATCH, DELETE | sí | no | no |
| `POST /campanias/{id}/aplicaciones` (proceso principal) | sí | sí | no |
| `POST /aplicaciones/{id}/autorizacion` y `/reevaluacion` | no | no | sí |
| Catálogo (cultivos, plagas, agroquímicos): POST, PUT, PATCH, DELETE | no | no | sí |
| Cualquier otra combinación | no | no | no |

Los nombres de los endpoints (`/autorizacion`, `/reevaluacion`, `/suelo`, `/suelo/refrescar`) son los de la Sección 07 del cuestionario
y los implementan ms-campos y ms-tratamientos (T07). Cada microservicio vuelve a controlar el rol con `@PreAuthorize`.
Supuesto para revisar en el grupo: que el OPERARIO no edite ni borre monitoreos.

Tests: `mvn test` en `gateway/` (no necesitan Keycloak ni los microservicios).
`SecurityConfigTest` recorre la matriz; `KeycloakJwtConverterTest` prueba la conversión de roles.

Prueba a mano (con el compose levantado y `$TOKEN` de `productor1`):

    # PRODUCTOR no administra el catálogo: 403
    curl -s -o /dev/null -w "%{http_code}" -X POST http://localhost:8080/api/v1/cultivos \
      -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{}'
    # Sin token: 401
    curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/v1/cultivos


## Correr en tu máquina

    mvn spring-boot:run

En Docker se pisan `CAMPOS_URL=http://ms-campos:8083`, `TRATAMIENTOS_URL=http://ms-tratamientos:8082`
y `JWT_JWK_SET_URI=http://keycloak:8080/realms/agro/protocol/openid-connect/certs`.
