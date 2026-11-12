package ar.edu.agro.gateway.config;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

/**
 * Prueba la matriz de autorización del gateway sin levantar Keycloak ni los microservicios.
 * mockJwt() entrega el usuario ya autenticado con sus roles. Cuando la regla PERMITE, el gateway intenta
 * reenviar al microservicio (que no está corriendo) y responde 5xx: lo que importa es que NO sea 401 ni 403.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class SecurityConfigTest {

    @Autowired
    WebTestClient client;

    @Test
    void sinTokenResponde401() {
        client.get().uri("/api/v1/establecimientos").exchange().expectStatus().isUnauthorized();
    }

    @ParameterizedTest(name = "{0} {1} {2} -> permitido={3}")
    @CsvSource({
            // rol, método, ruta, permitido
            "PRODUCTOR, GET,    /api/v1/establecimientos,                 true",
            "OPERARIO,  GET,    /api/v1/lotes/5,                          true",
            "AGRONOMO,  GET,    /api/v1/cultivos,                         true",
            "OPERARIO,  GET,    /api/v1/cultivos,                         true",

            "PRODUCTOR, POST,   /api/v1/establecimientos,                 true",
            "OPERARIO,  POST,   /api/v1/establecimientos,                 false",
            "AGRONOMO,  POST,   /api/v1/establecimientos,                 false",
            "PRODUCTOR, DELETE, /api/v1/campanias/3,                      true",
            "OPERARIO,  PUT,    /api/v1/campanias/3,                      false",

            "AGRONOMO,  POST,   /api/v1/cultivos,                         true",
            "PRODUCTOR, POST,   /api/v1/cultivos,                         false",
            "OPERARIO,  PUT,    /api/v1/agroquimicos/2,                   false",
            "AGRONOMO,  DELETE, /api/v1/plagas/2,                         true",
            "PRODUCTOR, DELETE, /api/v1/plagas/2,                         false",

            "OPERARIO,  POST,   /api/v1/campanias/3/monitoreos,           true",
            "PRODUCTOR, POST,   /api/v1/campanias/3/monitoreos,           true",
            "AGRONOMO,  POST,   /api/v1/campanias/3/monitoreos,           false",
            "OPERARIO,  DELETE, /api/v1/campanias/3/monitoreos/9,         false",

            "OPERARIO,  POST,   /api/v1/campanias/3/aplicaciones,         true",
            "PRODUCTOR, POST,   /api/v1/campanias/3/aplicaciones,         true",
            "AGRONOMO,  POST,   /api/v1/campanias/3/aplicaciones,         false",
            "AGRONOMO,  POST,   /api/v1/aplicaciones/7/autorizacion,      true",
            "PRODUCTOR, POST,   /api/v1/aplicaciones/7/autorizacion,      false",
            "OPERARIO,  POST,   /api/v1/aplicaciones/7/autorizacion,      false",
            "AGRONOMO,  POST,   /api/v1/aplicaciones/7/reevaluacion,      true",
            "OPERARIO,  POST,   /api/v1/aplicaciones/7/reevaluacion,      false",
            "AGRONOMO,  GET,    /api/v1/aplicaciones?estado=PENDIENTE_DE_VALIDACION, true",
            "PRODUCTOR, DELETE, /api/v1/aplicaciones/7,                   false",

            "AGRONOMO,  PATCH,  /api/v1/lotes/5/suelo,                    true",
            "PRODUCTOR, PATCH,  /api/v1/lotes/5/suelo,                    false",
            "OPERARIO,  PATCH,  /api/v1/lotes/5/suelo,                    false",
            "AGRONOMO,  POST,   /api/v1/lotes/5/suelo/refrescar,          true",
            "PRODUCTOR, POST,   /api/v1/lotes/5/suelo/refrescar,          false",
            "PRODUCTOR, POST,   /api/v1/lotes/5/campanias,                true",
            "AGRONOMO,  POST,   /api/v1/lotes/5/campanias,                false",
            "PRODUCTOR, POST,   /api/v1/establecimientos/1/lotes,         true",
            "AGRONOMO,  POST,   /api/v1/establecimientos/1/lotes,         false",

            "AGRONOMO,  GET,    /api/v1/evaluaciones/4,                   true",
            "PRODUCTOR, GET,    /api/v1/evaluaciones/4,                   false",
            "OPERARIO,  GET,    /api/v1/evaluaciones/4,                   false",
            "PRODUCTOR, POST,   /api/v1/evaluaciones,                     true",
            "PRODUCTOR, GET,    /api/v1/ruta-que-no-existe,               false"
    })
    void matrizDeAutorizacion(String rol, String metodo, String ruta, boolean permitido) {
        WebTestClient.ResponseSpec respuesta = client
                .mutateWith(mockJwt().authorities(new SimpleGrantedAuthority("ROLE_" + rol)))
                .method(HttpMethod.valueOf(metodo)).uri(ruta)
                .exchange();

        if (permitido) {
            respuesta.expectStatus().value(status -> assertThat(status).isNotIn(401, 403));
        } else {
            respuesta.expectStatus().isForbidden();
        }
    }

    @Test
    void usuarioAutenticadoSinRolDelDominioQuedaAfuera() {
        client.mutateWith(mockJwt()) // autenticado, sin ningún ROLE_*
                .get().uri("/api/v1/establecimientos").exchange().expectStatus().isForbidden();
    }
}
