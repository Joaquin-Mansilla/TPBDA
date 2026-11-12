package ar.edu.agro.campos.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import ar.edu.agro.campos.config.TratamientosProperties;
import ar.edu.agro.commons.contract.AplicacionPreviaDto;
import ar.edu.agro.commons.contract.ClaseTextura;
import ar.edu.agro.commons.contract.CultivoResumenResponse;
import ar.edu.agro.commons.contract.EvaluacionRequest;
import ar.edu.agro.commons.contract.EvaluacionResponse;
import ar.edu.agro.commons.contract.ResultadoEvaluacion;
import ar.edu.agro.commons.contract.SueloDto;
import ar.edu.agro.commons.contract.UbicacionDto;
import ar.edu.agro.commons.error.RecursoNoEncontradoException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Los tests no usan la red: simulan a ms-tratamientos con MockRestServiceServer. */
class TratamientosClientTest {

    private static final String BASE = "http://tratamientos.test";

    private MockRestServiceServer server;
    private TratamientosClient cliente;

    @BeforeEach
    void preparar() {
        Jwt jwt = Jwt.withTokenValue("token-del-usuario")
                .header("alg", "none")
                .claim("sub", "11111111-1111-1111-1111-000000000001")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_PRODUCTOR"))));

        // Igual que en la aplicación (Spring Boot): las fechas viajan como texto ISO, no como arreglo
        ObjectMapper mapper = Jackson2ObjectMapperBuilder.json()
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(BASE)
                .messageConverters(converters -> converters.add(0, new MappingJackson2HttpMessageConverter(mapper)))
                .requestInterceptor(new ReenvioTokenInterceptor());
        server = MockRestServiceServer.bindTo(builder).build();

        TratamientosProperties props = new TratamientosProperties(
                BASE, Duration.ofSeconds(2), Duration.ofSeconds(5), 1);
        cliente = new TratamientosClient(builder.build(), props);
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    private static final String CULTIVO_JSON =
            "{\"id\":7,\"nombre\":\"Soja\",\"familia\":\"Fabaceae\",\"diasCiclo\":150,"
                    + "\"mesSiembraDesde\":10,\"mesSiembraHasta\":12}";

    private static EvaluacionRequest pedido() {
        SueloDto suelo = new SueloDto(ClaseTextura.FRANCO, new BigDecimal("23.5"), new BigDecimal("41.2"),
                new BigDecimal("35.3"), new BigDecimal("6.2"), new BigDecimal("14.8"));
        return new EvaluacionRequest(7L, 2L, 3L, new BigDecimal("2.5"), new BigDecimal("40"),
                LocalDate.of(2026, 11, 20), LocalDate.of(2027, 4, 10), suelo,
                new UbicacionDto("Córdoba", "Capital", null),
                List.of(new AplicacionPreviaDto(LocalDate.of(2026, 9, 1))));
    }

    // ---------- GET /cultivos/{id} ----------

    @Test
    void obtenerCultivoReenviaElTokenYDevuelveElCultivo() {
        server.expect(method(HttpMethod.GET))
                .andExpect(requestTo(BASE + "/api/v1/cultivos/7"))
                .andExpect(header("Authorization", "Bearer token-del-usuario"))
                .andRespond(withSuccess(CULTIVO_JSON, MediaType.APPLICATION_JSON));

        CultivoResumenResponse cultivo = cliente.obtenerCultivo(7L);

        assertThat(cultivo.nombre()).isEqualTo("Soja");
        assertThat(cultivo.familia()).isEqualTo("Fabaceae");
        assertThat(cultivo.diasCiclo()).isEqualTo(150);
        assertThat(cultivo.mesSiembraDesde()).isEqualTo(10);
        assertThat(cultivo.mesSiembraHasta()).isEqualTo(12);
        server.verify();
    }

    @Test
    void unCultivoInexistenteEsUnRecursoNoEncontradoYNoSeReintenta() {
        server.expect(ExpectedCount.once(), requestTo(BASE + "/api/v1/cultivos/99"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> cliente.obtenerCultivo(99L))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("99");
        server.verify();
    }

    @Test
    void laConsultaDeCultivoSeReintentaUnaVezSiTratamientosFalla() {
        server.expect(ExpectedCount.once(), requestTo(BASE + "/api/v1/cultivos/7"))
                .andRespond(withServerError());
        server.expect(ExpectedCount.once(), requestTo(BASE + "/api/v1/cultivos/7"))
                .andRespond(withSuccess(CULTIVO_JSON, MediaType.APPLICATION_JSON));

        assertThat(cliente.obtenerCultivo(7L).id()).isEqualTo(7L);
        server.verify();
    }

    @Test
    void siTratamientosNoRespondeLaConsultaDeCultivoFalla() {
        server.expect(ExpectedCount.times(2), requestTo(BASE + "/api/v1/cultivos/7"))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() -> cliente.obtenerCultivo(7L))
                .isInstanceOf(ApiExternaException.class)
                .hasMessageContaining("no respondió");
        server.verify();
    }

    @Test
    void unCultivoConDatosFueraDeRangoSeTrataComoFalla() {
        String invalido = "{\"id\":7,\"nombre\":\"Soja\",\"familia\":\"Fabaceae\",\"diasCiclo\":0,"
                + "\"mesSiembraDesde\":10,\"mesSiembraHasta\":13}";
        server.expect(requestTo(BASE + "/api/v1/cultivos/7"))
                .andRespond(withSuccess(invalido, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> cliente.obtenerCultivo(7L))
                .isInstanceOf(ApiExternaException.class)
                .hasMessageContaining("incompletos o fuera de rango");
    }

    @Test
    void siTratamientosRechazaElTokenSeTrataComoNoDisponible() {
        // Un 401 acá no es culpa del usuario (Campos ya validó su token): suele ser un problema de configuración
        server.expect(ExpectedCount.once(), requestTo(BASE + "/api/v1/cultivos/7"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> cliente.obtenerCultivo(7L))
                .isInstanceOf(ApiExternaException.class)
                .hasMessageContaining("401");
        server.verify();
    }

    // ---------- POST /evaluaciones ----------

    @Test
    void evaluarConUnaReferenciaInexistenteEsUn404YNoSeReintenta() {
        server.expect(ExpectedCount.once(), requestTo(BASE + "/api/v1/evaluaciones"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .body("{\"status\":404,\"message\":\"No existe el agroquímico 3\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> cliente.evaluar(pedido()))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessage("No existe el agroquímico 3");
        server.verify();
    }

    @Test
    void evaluarEnviaElPedidoConElTokenYDevuelveLaDecision() {
        String respuesta = "{\"id\":55,\"resultado\":\"VIABLE\",\"motivos\":[],\"fechaSugerida\":null,"
                + "\"dosisTotal\":100.0,\"envases\":4,\"costo\":32000.50}";
        server.expect(method(HttpMethod.POST))
                .andExpect(requestTo(BASE + "/api/v1/evaluaciones"))
                .andExpect(header("Authorization", "Bearer token-del-usuario"))
                .andExpect(jsonPath("$.cultivoId").value(7))
                .andExpect(jsonPath("$.agroquimicoId").value(3))
                .andExpect(jsonPath("$.fechaAplicacion").value("2026-11-20"))
                .andExpect(jsonPath("$.cosechaEstimada").value("2027-04-10"))
                .andExpect(jsonPath("$.suelo.claseTextura").value("FRANCO"))
                .andExpect(jsonPath("$.ubicacion.provincia").value("Córdoba"))
                .andExpect(jsonPath("$.aplicacionesPrevias[0].fecha").value("2026-09-01"))
                .andRespond(withSuccess(respuesta, MediaType.APPLICATION_JSON));

        EvaluacionResponse evaluacion = cliente.evaluar(pedido());

        assertThat(evaluacion.id()).isEqualTo(55L);
        assertThat(evaluacion.resultado()).isEqualTo(ResultadoEvaluacion.VIABLE);
        assertThat(evaluacion.envases()).isEqualTo(4);
        assertThat(evaluacion.costo()).isEqualByComparingTo("32000.50");
        server.verify();
    }

    @Test
    void evaluarConLaFechaSugeridaEnReprogramar() {
        String respuesta = "{\"id\":56,\"resultado\":\"REPROGRAMAR\","
                + "\"motivos\":[{\"regla\":\"T3\",\"detalle\":\"Intervalo mínimo no cumplido\"}],"
                + "\"fechaSugerida\":\"2026-12-01\"}";
        server.expect(requestTo(BASE + "/api/v1/evaluaciones"))
                .andRespond(withSuccess(respuesta, MediaType.APPLICATION_JSON));

        EvaluacionResponse evaluacion = cliente.evaluar(pedido());

        assertThat(evaluacion.resultado()).isEqualTo(ResultadoEvaluacion.REPROGRAMAR);
        assertThat(evaluacion.fechaSugerida()).isEqualTo(LocalDate.of(2026, 12, 1));
        assertThat(evaluacion.motivos()).hasSize(1);
        assertThat(evaluacion.motivos().get(0).regla()).isEqualTo("T3");
    }

    @Test
    void evaluarNuncaSeReintentaPorqueTratamientosGuardaCadaEvaluacion() {
        // Si se reintentara, el servidor simulado rechazaría la segunda llamada y el test fallaría
        server.expect(ExpectedCount.once(), requestTo(BASE + "/api/v1/evaluaciones"))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() -> cliente.evaluar(pedido()))
                .isInstanceOf(ApiExternaException.class)
                .hasMessageContaining("no respondió");
        server.verify();
    }

    @Test
    void evaluarConUnErrorDelServidorFallaSinReintentar() {
        server.expect(ExpectedCount.once(), requestTo(BASE + "/api/v1/evaluaciones"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> cliente.evaluar(pedido()))
                .isInstanceOf(ApiExternaException.class);
        server.verify();
    }

    @Test
    void evaluarConUnPedidoInvalido400SeTrataComoFalla() {
        server.expect(ExpectedCount.once(), requestTo(BASE + "/api/v1/evaluaciones"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).body("{\"mensaje\":\"plagaId obligatorio\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> cliente.evaluar(pedido()))
                .isInstanceOf(ApiExternaException.class)
                .hasMessageContaining("400");
        server.verify();
    }

    @Test
    void unaDecisionSinResultadoSeTrataComoFalla() {
        server.expect(requestTo(BASE + "/api/v1/evaluaciones"))
                .andRespond(withSuccess("{\"id\":57}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> cliente.evaluar(pedido()))
                .isInstanceOf(ApiExternaException.class)
                .hasMessageContaining("sin resultado");
    }

    @Test
    void reprogramarSinFechaSugeridaSeTrataComoFalla() {
        server.expect(requestTo(BASE + "/api/v1/evaluaciones"))
                .andRespond(withSuccess("{\"id\":58,\"resultado\":\"REPROGRAMAR\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> cliente.evaluar(pedido()))
                .isInstanceOf(ApiExternaException.class)
                .hasMessageContaining("fecha sugerida");
    }

    // ---------- sin identidad ----------

    @Test
    void sinTokenEnElContextoNoSeLlamaATratamientos() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(() -> cliente.obtenerCultivo(7L))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cliente.evaluar(pedido()))
                .isInstanceOf(IllegalStateException.class);
        server.verify();
    }
}
