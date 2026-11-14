package ar.edu.agro.campos.client;

import ar.edu.agro.campos.client.dto.SoilGridsResponse;
import ar.edu.agro.campos.config.ApisExternasProperties;
import ar.edu.agro.commons.contract.ClaseTextura;
import ar.edu.agro.commons.contract.SueloDto;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Consulta SoilGrids (ISRIC) por coordenadas y devuelve el suelo del punto como SueloDto
 * (porcentajes de arcilla, arena y limo, pH, carbono orgánico y clase de textura calculada).
 *
 * Es un dato orientativo (resolución de 250 m o más), no una medición. Se consulta una sola vez por lote
 * y el resultado se guarda en el lote, porque el servicio está en fase beta y limita las consultas.
 */
@Component
public class SoilGridsClient extends ClienteApiExterna {

    private static final String API = "SoilGrids";
    private static final BigDecimal CIEN = new BigDecimal("100");
    private static final BigDecimal PH_MAXIMO = new BigDecimal("14");

    private final RestClient restClient;
    private final String profundidad;
    private final ClasificadorTextura clasificador;

    public SoilGridsClient(@Qualifier("soilGridsRestClient") RestClient restClient,
                           ApisExternasProperties props,
                           ClasificadorTextura clasificador) {
        super(API, props.soilgrids().reintentos());
        this.restClient = restClient;
        this.profundidad = props.soilgrids().profundidad();
        this.clasificador = clasificador;
    }

    /**
     * @throws ApiExternaException si SoilGrids no responde, responde un error o devuelve datos inválidos
     */
    public SueloDto consultarSuelo(BigDecimal latitud, BigDecimal longitud) {
        SoilGridsResponse respuesta = ejecutar(() -> restClient.get()
                .uri(uri -> uri.path("/properties/query")
                        .queryParam("lon", longitud.toPlainString())
                        .queryParam("lat", latitud.toPlainString())
                        .queryParam("property", "clay", "sand", "silt", "phh2o", "soc")
                        .queryParam("depth", profundidad)
                        .queryParam("value", "mean")
                        .build())
                .retrieve()
                .body(SoilGridsResponse.class));

        return armarSuelo(respuesta);
    }

    private SueloDto armarSuelo(SoilGridsResponse respuesta) {
        if (respuesta.properties() == null || respuesta.properties().layers() == null) {
            throw inesperado("sin capas");
        }

        BigDecimal arcilla = valorDeCapa(respuesta, "clay");
        BigDecimal arena = valorDeCapa(respuesta, "sand");
        BigDecimal limo = valorDeCapa(respuesta, "silt");
        BigDecimal ph = valorDeCapa(respuesta, "phh2o");
        BigDecimal carbono = valorDeCapa(respuesta, "soc");

        entre(arcilla, BigDecimal.ZERO, CIEN, "clay");
        entre(arena, BigDecimal.ZERO, CIEN, "sand");
        entre(limo, BigDecimal.ZERO, CIEN, "silt");
        entre(ph, BigDecimal.ZERO, PH_MAXIMO, "phh2o");
        if (carbono.signum() < 0) {
            throw inesperado("soc negativo");
        }

        ClaseTextura clase = clasificador.clasificar(arena, arcilla);
        return new SueloDto(clase, arcilla, arena, limo, ph, carbono);
    }

    /** Valor de la capa en la profundidad pedida, ya dividido por su factor de conversión. */
    private BigDecimal valorDeCapa(SoilGridsResponse respuesta, String nombre) {
        for (SoilGridsResponse.Layer capa : respuesta.properties().layers()) {
            if (!nombre.equals(capa.name())) {
                continue;
            }
            if (capa.unitMeasure() == null || capa.unitMeasure().dFactor() == null
                    || capa.unitMeasure().dFactor().signum() <= 0) {
                throw inesperado("capa " + nombre + " sin factor de conversión");
            }
            if (capa.depths() == null) {
                throw inesperado("capa " + nombre + " sin profundidades");
            }
            for (SoilGridsResponse.Depth profundidadCapa : capa.depths()) {
                if (!this.profundidad.equals(profundidadCapa.label())) {
                    continue;
                }
                if (profundidadCapa.values() == null || profundidadCapa.values().mean() == null) {
                    // SoilGrids devuelve null donde no hay dato (agua, zonas urbanas, etc.)
                    throw inesperado("sin dato de " + nombre + " en " + this.profundidad);
                }
                return profundidadCapa.values().mean()
                        .divide(capa.unitMeasure().dFactor(), 1, RoundingMode.HALF_UP);
            }
            throw inesperado("capa " + nombre + " sin la profundidad " + this.profundidad);
        }
        throw inesperado("falta la capa " + nombre);
    }

    private void entre(BigDecimal valor, BigDecimal minimo, BigDecimal maximo, String nombre) {
        if (valor.compareTo(minimo) < 0 || valor.compareTo(maximo) > 0) {
            throw inesperado(nombre + " fuera de rango: " + valor);
        }
    }
}
