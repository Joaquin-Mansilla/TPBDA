package ar.edu.agro.campos.client;

import ar.edu.agro.campos.client.dto.GeorefResponse;
import ar.edu.agro.campos.config.ApisExternasProperties;
import ar.edu.agro.commons.contract.UbicacionDto;
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Consulta Georef (datos.gob.ar) por coordenadas y devuelve la ubicación administrativa del punto
 * (provincia, departamento y municipio). Es la base de la regla T5 (restricción territorial).
 */
@Component
public class GeorefClient extends ClienteApiExterna {

    private static final String API = "Georef";

    private final RestClient restClient;

    public GeorefClient(@Qualifier("georefRestClient") RestClient restClient, ApisExternasProperties props) {
        super(API, props.georef().reintentos());
        this.restClient = restClient;
    }

    /**
     * @throws ApiExternaException si Georef no responde, responde un error o no ubica el punto
     *                             (por ejemplo, coordenadas fuera de Argentina)
     */
    public UbicacionDto consultarUbicacion(BigDecimal latitud, BigDecimal longitud) {
        GeorefResponse respuesta = ejecutar(() -> restClient.get()
                .uri(uri -> uri.path("/ubicacion")
                        .queryParam("lat", latitud.toPlainString())
                        .queryParam("lon", longitud.toPlainString())
                        .build())
                .retrieve()
                .body(GeorefResponse.class));

        GeorefResponse.Ubicacion ubicacion = respuesta.ubicacion();
        if (ubicacion == null) {
            throw inesperado("sin ubicación");
        }

        String provincia = texto(ubicacion.provincia());
        if (provincia == null) {
            // Fuera de Argentina Georef responde 200 con los nombres en null
            throw inesperado("el punto no corresponde a una provincia argentina");
        }
        return new UbicacionDto(provincia, texto(ubicacion.departamento()), texto(ubicacion.municipio()));
    }

    private String texto(GeorefResponse.Nombre nombre) {
        if (nombre == null || nombre.nombre() == null || nombre.nombre().isBlank()) {
            return null;
        }
        return nombre.nombre().trim();
    }
}
