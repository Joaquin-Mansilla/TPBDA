package ar.edu.agro.campos.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;

import ar.edu.agro.campos.config.TratamientosProperties;
import ar.edu.agro.commons.contract.EvaluacionRequest;
import java.math.BigDecimal;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.match.MockRestRequestMatchers;
import org.springframework.web.client.RestClient;

/**
 * T12: ante una caída o un timeout de Tratamientos, el cliente lanza ApiExternaException y distingue
 * timeout (504 en el controller) de caída (503). Sin red: MockRestServiceServer simula a ms-tratamientos.
 */
class ToleranciaAFallosClienteTest {

    private static final String BASE = "http://tratamientos.test";

    private MockRestServiceServer server;
    private TratamientosClient cliente;

    @BeforeEach
    void preparar() {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").claim("sub", "u1")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_PRODUCTOR"))));
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE).requestInterceptor(new ReenvioTokenInterceptor());
        server = MockRestServiceServer.bindTo(builder).build();
        // un reintento en las consultas, como en el application.yml
        cliente = new TratamientosClient(builder.build(),
                new TratamientosProperties(BASE, Duration.ofSeconds(2), Duration.ofSeconds(5), 1));
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    private static EvaluacionRequest pedido() {
        return new EvaluacionRequest(7L, 2L, 3L, new BigDecimal("2.5"), new BigDecimal("40"),
                LocalDate.of(2026, 11, 20), LocalDate.of(2027, 4, 10), null, null, List.of());
    }

    @Test
    void timeoutDeLecturaEsApiExternaExceptionConTimeoutYSeReintentaUnaVez() {
        server.expect(ExpectedCount.times(2), MockRestRequestMatchers.requestTo(BASE + "/api/v1/cultivos/7"))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() -> cliente.obtenerCultivo(7L))
                .isInstanceOfSatisfying(ApiExternaException.class, e -> assertThat(e.esTimeout()).isTrue());
        server.verify();
    }

    @Test
    void conexionRechazadaEsApiExternaExceptionSinTimeout() {
        server.expect(ExpectedCount.times(2), MockRestRequestMatchers.requestTo(BASE + "/api/v1/cultivos/7"))
                .andRespond(withException(new ConnectException("Connection refused")));

        assertThatThrownBy(() -> cliente.obtenerCultivo(7L))
                .isInstanceOfSatisfying(ApiExternaException.class, e -> assertThat(e.esTimeout()).isFalse());
    }

    @Test
    void errorDelServidorEsApiExternaExceptionSinTimeout() {
        server.expect(ExpectedCount.times(2), MockRestRequestMatchers.requestTo(BASE + "/api/v1/cultivos/7"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> cliente.obtenerCultivo(7L))
                .isInstanceOfSatisfying(ApiExternaException.class, e -> assertThat(e.esTimeout()).isFalse());
    }

    @Test
    void timeoutAlEvaluarNoSeReintenta() {
        // evaluar() deja un registro en Tratamientos: repetirlo tras un timeout podría duplicarlo
        server.expect(ExpectedCount.once(), MockRestRequestMatchers.requestTo(BASE + "/api/v1/evaluaciones"))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() -> cliente.evaluar(pedido()))
                .isInstanceOfSatisfying(ApiExternaException.class, e -> assertThat(e.esTimeout()).isTrue());
        server.verify();
    }

    @Test
    void esTimeoutReconoceLaCausaAunqueVengaEnvuelta() {
        assertThat(ClienteApiExterna.esTimeout(new RuntimeException("x", new SocketTimeoutException("t")))).isTrue();
        assertThat(ClienteApiExterna.esTimeout(new ConnectException("Connection refused"))).isFalse();
        assertThat(ClienteApiExterna.esTimeout(new RuntimeException("sin causa"))).isFalse();
    }
}
