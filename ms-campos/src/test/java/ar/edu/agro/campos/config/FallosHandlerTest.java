package ar.edu.agro.campos.config;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.agro.campos.client.ApiExternaException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.transaction.CannotCreateTransactionException;

/** T12: el código HTTP correcto para cada tipo de falla (sin red, sin base de datos y sin Keycloak). */
class FallosHandlerTest {

    private final FallosHandler handler = new FallosHandler();
    private final MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/lotes/1/campanias");

    @Test
    void tratamientosCaidoEs503() {
        var r = handler.servicioExterno(new ApiExternaException("Tratamientos", "no respondió (Connection refused)"), req);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(r.getHeaders().getFirst("Retry-After")).isEqualTo("5");
        assertThat(r.getBody().status()).isEqualTo(503);
        assertThat(r.getBody().path()).isEqualTo("/api/v1/lotes/1/campanias");
        assertThat(r.getBody().message()).contains("Tratamientos");
    }

    @Test
    void tratamientosQueNoContestaATiempoEs504() {
        var r = handler.servicioExterno(new ApiExternaException("Tratamientos", "no respondió (Read timed out)").conTimeout(), req);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(r.getBody().status()).isEqualTo(504);
        assertThat(r.getHeaders().getFirst("Retry-After")).isEqualTo("5");
    }

    @Test
    void baseDeDatosCaidaEs503() {
        assertThat(handler.baseNoDisponible(new DataAccessResourceFailureException("Connection refused"), req).getStatusCode())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(handler.baseNoDisponible(new CannotCreateTransactionException("no hay conexiones"), req).getStatusCode())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void consultaQueExcedeElTiempoEs504YNoRevelaElDetalle() {
        var r = handler.baseLenta(new QueryTimeoutException("select * from campos.lote ..."), req);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(r.getBody().message()).doesNotContain("select");
    }

    @Test
    void keycloakCaidoSinClavesEnCacheEs503() throws Exception {
        MockHttpServletResponse res = new MockHttpServletResponse();

        new IdentidadNoDisponibleEntryPoint().commence(req, res,
                new AuthenticationServiceException("Couldn't retrieve remote JWK set"));

        assertThat(res.getStatus()).isEqualTo(503);
        assertThat(res.getHeader("Retry-After")).isEqualTo("5");
        assertThat(res.getContentAsString()).contains("\"status\":503");
    }

    @Test
    void tokenInvalidoSigueSiendo401() throws Exception {
        MockHttpServletResponse res = new MockHttpServletResponse();

        new IdentidadNoDisponibleEntryPoint().commence(req, res, new InvalidBearerTokenException("firma inválida"));

        assertThat(res.getStatus()).isEqualTo(401);
    }
}
