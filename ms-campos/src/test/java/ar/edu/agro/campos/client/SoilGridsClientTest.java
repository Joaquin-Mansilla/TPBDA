package ar.edu.agro.campos.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import ar.edu.agro.campos.config.ApisExternasProperties;
import ar.edu.agro.commons.contract.ClaseTextura;
import ar.edu.agro.commons.contract.SueloDto;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Los tests no usan la red: simulan las respuestas de SoilGrids con MockRestServiceServer. */
class SoilGridsClientTest {

    private static final BigDecimal LAT = new BigDecimal("-31.42");
    private static final BigDecimal LON = new BigDecimal("-64.18");

    private MockRestServiceServer server;
    private SoilGridsClient cliente;

    @BeforeEach
    void preparar() {
        ApisExternasProperties props = PropiedadesDePrueba.con(1);
        RestClient.Builder builder = RestClient.builder().baseUrl(props.soilgrids().urlBase());
        server = MockRestServiceServer.bindTo(builder).build();
        cliente = new SoilGridsClient(builder.build(), props, new ClasificadorTextura(props));
    }

    /** JSON con la forma de SoilGrids v2.0: valores enteros con d_factor 10. */
    private static String json(Integer arcilla, Integer arena, Integer limo, Integer ph, Integer carbono) {
        return "{\"type\":\"Feature\",\"geometry\":{\"type\":\"Point\",\"coordinates\":[-64.18,-31.42]},"
                + "\"properties\":{\"layers\":["
                + capa("clay", arcilla) + ","
                + capa("sand", arena) + ","
                + capa("silt", limo) + ","
                + capa("phh2o", ph) + ","
                + capa("soc", carbono)
                + "]}}";
    }

    private static String capa(String nombre, Integer valor) {
        String mean = valor == null ? "null" : String.valueOf(valor);
        return "{\"name\":\"" + nombre + "\",\"unit_measure\":{\"d_factor\":10,\"mapped_units\":\"x\","
                + "\"target_units\":\"y\",\"uncertainty_unit\":\"\"},"
                + "\"depths\":[{\"range\":{\"top_depth\":0,\"bottom_depth\":5,\"unit_depth\":\"cm\"},"
                + "\"label\":\"0-5cm\",\"values\":{\"mean\":" + mean + "}}]}";
    }

    private void respuestaOk(String cuerpo) {
        server.expect(requestTo(Matchers.containsString("/properties/query")))
                .andRespond(withSuccess(cuerpo, MediaType.APPLICATION_JSON));
    }

    @Test
    void convierteLosValoresConSuFactorYCalculaLaTextura() {
        server.expect(method(HttpMethod.GET))
                .andExpect(requestTo(Matchers.containsString("/properties/query")))
                .andExpect(queryParam("lat", "-31.42"))
                .andExpect(queryParam("lon", "-64.18"))
                .andExpect(queryParam("property", "clay", "sand", "silt", "phh2o", "soc"))
                .andExpect(queryParam("depth", "0-5cm"))
                .andExpect(queryParam("value", "mean"))
                .andRespond(withSuccess(json(235, 412, 353, 62, 148), MediaType.APPLICATION_JSON));

        SueloDto suelo = cliente.consultarSuelo(LAT, LON);

        assertThat(suelo.arcillaPct()).isEqualByComparingTo("23.5");
        assertThat(suelo.arenaPct()).isEqualByComparingTo("41.2");
        assertThat(suelo.limoPct()).isEqualByComparingTo("35.3");
        assertThat(suelo.ph()).isEqualByComparingTo("6.2");
        assertThat(suelo.carbonoOrganicoGKg()).isEqualByComparingTo("14.8");
        assertThat(suelo.claseTextura()).isEqualTo(ClaseTextura.FRANCO);
        server.verify();
    }

    @Test
    void sueloConMuchaArenaEsArenoso() {
        respuestaOk(json(150, 560, 290, 60, 100));
        assertThat(cliente.consultarSuelo(LAT, LON).claseTextura()).isEqualTo(ClaseTextura.ARENOSO);
    }

    @Test
    void sueloConMuchaArcillaEsArcilloso() {
        respuestaOk(json(400, 200, 400, 65, 200));
        assertThat(cliente.consultarSuelo(LAT, LON).claseTextura()).isEqualTo(ClaseTextura.ARCILLOSO);
    }

    @Test
    void siUnaCapaNoTieneDatoSeTrataComoFalla() {
        respuestaOk(json(null, 412, 353, 62, 148));

        assertThatThrownBy(() -> cliente.consultarSuelo(LAT, LON))
                .isInstanceOf(ApiExternaException.class)
                .hasMessageContaining("sin dato de clay");
    }

    @Test
    void siElPhEstaFueraDeRangoSeTrataComoFalla() {
        respuestaOk(json(235, 412, 353, 150, 148));

        assertThatThrownBy(() -> cliente.consultarSuelo(LAT, LON))
                .isInstanceOf(ApiExternaException.class)
                .hasMessageContaining("phh2o fuera de rango");
    }

    @Test
    void siFaltaUnaCapaSeTrataComoFalla() {
        respuestaOk("{\"properties\":{\"layers\":[" + capa("clay", 235) + "]}}");

        assertThatThrownBy(() -> cliente.consultarSuelo(LAT, LON))
                .isInstanceOf(ApiExternaException.class)
                .hasMessageContaining("falta la capa");
    }

    @Test
    void unJsonQueNoSePuedeLeerSeTrataComoFalla() {
        respuestaOk("esto no es json");

        assertThatThrownBy(() -> cliente.consultarSuelo(LAT, LON))
                .isInstanceOf(ApiExternaException.class);
    }

    @Test
    void reintentaUnaVezSiElServidorFalla() {
        server.expect(ExpectedCount.once(), requestTo(Matchers.containsString("/properties/query")))
                .andRespond(withServerError());
        respuestaOk(json(235, 412, 353, 62, 148));

        SueloDto suelo = cliente.consultarSuelo(LAT, LON);

        assertThat(suelo.claseTextura()).isEqualTo(ClaseTextura.FRANCO);
        server.verify();
    }

    @Test
    void siFallaTambienElReintentoLanzaLaExcepcion() {
        server.expect(ExpectedCount.times(2), requestTo(Matchers.containsString("/properties/query")))
                .andRespond(withServerError());

        assertThatThrownBy(() -> cliente.consultarSuelo(LAT, LON))
                .isInstanceOf(ApiExternaException.class);
        server.verify();
    }

    @Test
    void unTimeoutSeReintentaYDespuesFalla() {
        server.expect(ExpectedCount.times(2), requestTo(Matchers.containsString("/properties/query")))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() -> cliente.consultarSuelo(LAT, LON))
                .isInstanceOf(ApiExternaException.class)
                .hasMessageContaining("no respondió");
        server.verify();
    }

    @Test
    void unErrorDelCliente429NoSeReintenta() {
        // Si se reintentara, el servidor simulado rechazaría la segunda llamada y el test fallaría.
        server.expect(ExpectedCount.once(), requestTo(Matchers.containsString("/properties/query")))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> cliente.consultarSuelo(LAT, LON))
                .isInstanceOf(ApiExternaException.class)
                .hasMessageContaining("429");
        server.verify();
    }

    @Test
    void unaFallaDeConexionSeTraduceAApiExternaException() {
        server.expect(ExpectedCount.times(2), requestTo(Matchers.containsString("/properties/query")))
                .andRespond(withException(new IOException("conexión rechazada")));

        assertThatThrownBy(() -> cliente.consultarSuelo(LAT, LON))
                .isInstanceOf(ApiExternaException.class);
    }
}
