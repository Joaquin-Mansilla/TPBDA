package ar.edu.agro.campos.client;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.agro.campos.config.ApisExternasProperties;
import ar.edu.agro.commons.contract.SueloDto;
import ar.edu.agro.commons.contract.UbicacionDto;
import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Prueba contra las APIs REALES (necesita Internet). Está apagada por defecto para que "mvn test" no dependa
 * de servicios externos. Correrla una vez para confirmar que el parser entiende las respuestas reales:
 *
 *     PROBAR_APIS_REALES=true mvn test -Dtest=ApisExternasRealesTest
 *
 * En PowerShell:  $env:PROBAR_APIS_REALES="true"; mvn test "-Dtest=ApisExternasRealesTest"
 *
 * Respeta el límite de SoilGrids (5 consultas por minuto): hace una sola.
 */
@EnabledIfEnvironmentVariable(named = "PROBAR_APIS_REALES", matches = "true")
class ApisExternasRealesTest {

    // Córdoba capital
    private static final BigDecimal LAT = new BigDecimal("-31.42");
    private static final BigDecimal LON = new BigDecimal("-64.18");

    private final ApisExternasProperties props = new ApisExternasProperties(true,
            new ApisExternasProperties.SoilGrids("https://rest.isric.org/soilgrids/v2.0", "0-5cm",
                    Duration.ofSeconds(5), Duration.ofSeconds(20), 1),
            new ApisExternasProperties.Georef("https://apis.datos.gob.ar/georef/api/v2.0",
                    Duration.ofSeconds(5), Duration.ofSeconds(10), 1),
            new ApisExternasProperties.Textura(new BigDecimal("50"), new BigDecimal("35")));

    private RestClient restClient(String urlBase, int lecturaSegundos) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(lecturaSegundos * 1000);
        return RestClient.builder()
                .baseUrl(urlBase)
                .requestFactory(factory)
                .requestInterceptor(new LlamadaExternaInterceptor("real"))
                .build();
    }

    @Test
    void soilGridsRealDevuelveUnSueloCoherente() {
        SoilGridsClient cliente = new SoilGridsClient(
                restClient(props.soilgrids().urlBase(), 20), props, new ClasificadorTextura(props));

        SueloDto suelo = cliente.consultarSuelo(LAT, LON);

        System.out.println("SoilGrids real -> " + suelo);
        assertThat(suelo.claseTextura()).isNotNull();
        assertThat(suelo.arcillaPct()).isBetween(BigDecimal.ZERO, new BigDecimal("100"));
        assertThat(suelo.arenaPct()).isBetween(BigDecimal.ZERO, new BigDecimal("100"));
        assertThat(suelo.limoPct()).isBetween(BigDecimal.ZERO, new BigDecimal("100"));
        assertThat(suelo.ph()).isBetween(new BigDecimal("3"), new BigDecimal("10"));
    }

    @Test
    void georefRealUbicaCordobaCapital() {
        GeorefClient cliente = new GeorefClient(restClient(props.georef().urlBase(), 10), props);

        UbicacionDto ubicacion = cliente.consultarUbicacion(LAT, LON);

        System.out.println("Georef real -> " + ubicacion);
        assertThat(ubicacion.provincia()).containsIgnoringCase("rdoba");
        assertThat(ubicacion.departamento()).isNotBlank();
    }
}
