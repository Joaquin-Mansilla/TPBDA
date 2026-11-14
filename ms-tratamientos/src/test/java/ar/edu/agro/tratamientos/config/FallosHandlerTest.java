package ar.edu.agro.tratamientos.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.transaction.CannotCreateTransactionException;

/** T12: códigos HTTP de ms-tratamientos ante la base de datos o Keycloak caídos. */
class FallosHandlerTest {

    private final FallosHandler handler = new FallosHandler();
    private final MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/evaluaciones");

    @Test
    void baseDeDatosCaidaEs503ConRetryAfter() {
        var r = handler.baseNoDisponible(new DataAccessResourceFailureException("Connection refused"), req);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(r.getHeaders().getFirst("Retry-After")).isEqualTo("5");
        assertThat(r.getBody().path()).isEqualTo("/api/v1/evaluaciones");
        assertThat(handler.baseNoDisponible(new CannotCreateTransactionException("sin conexiones"), req).getStatusCode())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void consultaQueExcedeElTiempoEs504() {
        assertThat(handler.baseLenta(new QueryTimeoutException("timeout"), req).getStatusCode())
                .isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
    }

    @Test
    void keycloakCaidoSinClavesEnCacheEs503() throws Exception {
        MockHttpServletResponse res = new MockHttpServletResponse();

        new IdentidadNoDisponibleEntryPoint().commence(req, res, new AuthenticationServiceException("JWKS no disponible"));

        assertThat(res.getStatus()).isEqualTo(503);
        assertThat(res.getHeader("Retry-After")).isEqualTo("5");
    }

    @Test
    void tokenInvalidoSigueSiendo401() throws Exception {
        MockHttpServletResponse res = new MockHttpServletResponse();

        new IdentidadNoDisponibleEntryPoint().commence(req, res, new InvalidBearerTokenException("vencido"));

        assertThat(res.getStatus()).isEqualTo(401);
    }
}
