package ar.edu.agro.campos.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import ar.edu.agro.campos.config.ApisExternasProperties;
import ar.edu.agro.commons.contract.UbicacionDto;
import java.math.BigDecimal;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Los tests no usan la red: simulan las respuestas de Georef con MockRestServiceServer. */
class GeorefClientTest {

    private static final BigDecimal LAT = new BigDecimal("-31.42");
    private static final BigDecimal LON = new BigDecimal("-64.18");

    private MockRestServiceServer server;
    private GeorefClient cliente;

    @BeforeEach
    void preparar() {
        ApisExternasProperties props = PropiedadesDePrueba.con(1);
        RestClient.Builder builder = RestClient.builder().baseUrl(props.georef().urlBase());
        server = MockRestServiceServer.bindTo(builder).build();
        cliente = new GeorefClient(builder.build(), props);
    }

    private static String json(String provincia, String departamento, String municipio) {
        return "{\"cantidad\":1,\"inicio\":0,\"parametros\":{\"lat\":-31.42,\"lon\":-64.18},\"total\":1,"
                + "\"ubicacion\":{"
                + "\"departamento\":{\"id\":\"14014\",\"nombre\":" + comillas(departamento) + "},"
                + "\"lat\":-31.42,\"lon\":-64.18,"
                + "\"municipio\":{\"id\":\"140140\",\"nombre\":" + comillas(municipio) + "},"
                + "\"provincia\":{\"id\":\"14\",\"nombre\":" + comillas(provincia) + "}}}";
    }

    private static String comillas(String valor) {
        return valor == null ? "null" : "\"" + valor + "\"";
    }

    private void respuestaOk(String cuerpo) {
        server.expect(requestTo(Matchers.containsString("/ubicacion")))
                .andRespond(withSuccess(cuerpo, MediaType.APPLICATION_JSON));
    }

    @Test
    void devuelveProvinciaDepartamentoYMunicipio() {
        server.expect(method(HttpMethod.GET))
                .andExpect(requestTo(Matchers.containsString("/ubicacion")))
                .andExpect(queryParam("lat", "-31.42"))
                .andExpect(queryParam("lon", "-64.18"))
                .andRespond(withSuccess(json("Córdoba", "Capital", "Córdoba"), MediaType.APPLICATION_JSON));

        UbicacionDto ubicacion = cliente.consultarUbicacion(LAT, LON);

        assertThat(ubicacion.provincia()).isEqualTo("Córdoba");
        assertThat(ubicacion.departamento()).isEqualTo("Capital");
        assertThat(ubicacion.municipio()).isEqualTo("Córdoba");
        server.verify();
    }

    @Test
    void unMunicipioVacioQuedaEnNull() {
        respuestaOk(json("Córdoba", "Río Cuarto", ""));

        UbicacionDto ubicacion = cliente.consultarUbicacion(LAT, LON);

        assertThat(ubicacion.provincia()).isEqualTo("Córdoba");
        assertThat(ubicacion.municipio()).isNull();
    }

    @Test
    void unPuntoFueraDeArgentinaSeTrataComoFalla() {
        respuestaOk(json(null, null, null));

        assertThatThrownBy(() -> cliente.consultarUbicacion(LAT, LON))
                .isInstanceOf(ApiExternaException.class)
                .hasMessageContaining("provincia");
    }

    @Test
    void unaRespuestaSinUbicacionSeTrataComoFalla() {
        respuestaOk("{\"cantidad\":0,\"total\":0}");

        assertThatThrownBy(() -> cliente.consultarUbicacion(LAT, LON))
                .isInstanceOf(ApiExternaException.class);
    }

    @Test
    void unError400NoSeReintenta() {
        server.expect(ExpectedCount.once(), requestTo(Matchers.containsString("/ubicacion")))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> cliente.consultarUbicacion(LAT, LON))
                .isInstanceOf(ApiExternaException.class)
                .hasMessageContaining("400");
        server.verify();
    }

    @Test
    void reintentaUnaVezSiElServidorFalla() {
        server.expect(ExpectedCount.once(), requestTo(Matchers.containsString("/ubicacion")))
                .andRespond(withServerError());
        respuestaOk(json("Córdoba", "Capital", "Córdoba"));

        assertThat(cliente.consultarUbicacion(LAT, LON).provincia()).isEqualTo("Córdoba");
        server.verify();
    }
}
