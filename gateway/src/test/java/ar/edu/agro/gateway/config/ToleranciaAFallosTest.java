package ar.edu.agro.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.handler.timeout.ReadTimeoutException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.web.server.ResponseStatusException;

/** T12: los códigos que devuelve el gateway ante caídas y timeouts (sin red, sin Keycloak, sin microservicios). */
class ToleranciaAFallosTest {

    private final ErroresGatewayHandler handler = new ErroresGatewayHandler(new ObjectMapper());

    private MockServerWebExchange pedido() {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/cultivos").build());
    }

    private MockServerWebExchange manejar(Throwable error) {
        MockServerWebExchange exchange = pedido();
        handler.handle(exchange, error).block();
        return exchange;
    }

    @Test
    void microservicioCaidoConexionRechazadaEs503() {
        var exchange = manejar(new ConnectException("Connection refused"));

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(exchange.getResponse().getHeaders().getFirst("Retry-After")).isEqualTo("5");
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("\"status\":503").contains("/api/v1/cultivos");
    }

    @Test
    void hostInexistenteEs503() {
        assertThat(manejar(new UnknownHostException("ms-tratamientos")).getResponse().getStatusCode())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void sinInstanciaParaLaRutaEs503() {
        var exchange = manejar(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Unable to find instance"));
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void timeoutDeRespuestaEs504() {
        assertThat(manejar(ReadTimeoutException.INSTANCE).getResponse().getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(manejar(new TimeoutException("response timeout")).getResponse().getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(manejar(new SocketTimeoutException("Read timed out")).getResponse().getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
    }

    @Test
    void timeoutEnvueltoEnOtraExcepcionTambienEs504() {
        var exchange = manejar(new RuntimeException("falló la llamada", new TimeoutException("response timeout")));
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
    }

    @Test
    void conexionCortadaEs502() {
        var exchange = manejar(new java.io.IOException("Connection reset by peer"));
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    @Test
    void unaRutaInexistenteNoSeToca() {
        assertThat(ErroresGatewayHandler.clasificar(new ResponseStatusException(HttpStatus.NOT_FOUND))).isNull();
        assertThat(ErroresGatewayHandler.clasificar(new IllegalStateException("bug"))).isNull();
    }

    @Test
    void keycloakCaidoSinClavesEnCacheEs503() {
        MockServerWebExchange exchange = pedido();
        new IdentidadNoDisponibleEntryPoint()
                .commence(exchange, new AuthenticationServiceException("Couldn't retrieve remote JWK set")).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(exchange.getResponse().getHeaders().getFirst("Retry-After")).isEqualTo("5");
    }

    @Test
    void tokenInvalidoSigueSiendo401() {
        MockServerWebExchange exchange = pedido();
        new IdentidadNoDisponibleEntryPoint().commence(exchange, new InvalidBearerTokenException("firma inválida")).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
