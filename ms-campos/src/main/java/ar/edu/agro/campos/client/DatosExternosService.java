package ar.edu.agro.campos.client;

import ar.edu.agro.campos.config.ApisExternasProperties;
import ar.edu.agro.commons.contract.SueloDto;
import ar.edu.agro.commons.contract.UbicacionDto;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Punto de entrada para el resto de ms-campos: consulta SoilGrids y Georef por las coordenadas del lote.
 *
 * - consultar(): para POST /establecimientos/{id}/lotes. Con agro.externas.habilitado=false no llama a nada. Es tolerante: si una API falla, ese dato
 *   queda en null y el lote se crea igual. Nunca se inventa un valor.
 * - consultarSuelo() y consultarUbicacion(): para POST /lotes/{id}/suelo/refrescar. Son estrictos:
 *   si la API falla lanzan ApiExternaException, que el GlobalExceptionHandler traduce a 503.
 */
@Service
public class DatosExternosService {

    private static final Logger log = LoggerFactory.getLogger(DatosExternosService.class);

    private final SoilGridsClient soilGrids;
    private final GeorefClient georef;
    private final boolean habilitado;

    public DatosExternosService(SoilGridsClient soilGrids, GeorefClient georef, ApisExternasProperties props) {
        this.soilGrids = soilGrids;
        this.georef = georef;
        this.habilitado = props.habilitado();
    }

    public DatosExternosLote consultar(BigDecimal latitud, BigDecimal longitud) {
        if (!habilitado) {
            log.info("Consultas a SoilGrids y Georef deshabilitadas: el lote ({}, {}) queda sin suelo ni ubicación", latitud, longitud);
            return new DatosExternosLote(null, null);
        }
        SueloDto suelo = null;
        try {
            suelo = soilGrids.consultarSuelo(latitud, longitud);
        } catch (ApiExternaException e) {
            log.warn("El lote ({}, {}) se crea sin suelo: {}", latitud, longitud, e.getMessage());
        }

        UbicacionDto ubicacion = null;
        try {
            ubicacion = georef.consultarUbicacion(latitud, longitud);
        } catch (ApiExternaException e) {
            log.warn("El lote ({}, {}) se crea sin ubicación: {}", latitud, longitud, e.getMessage());
        }

        return new DatosExternosLote(suelo, ubicacion);
    }

    public SueloDto consultarSuelo(BigDecimal latitud, BigDecimal longitud) {
        exigirHabilitado("SoilGrids");
        return soilGrids.consultarSuelo(latitud, longitud);
    }

    public UbicacionDto consultarUbicacion(BigDecimal latitud, BigDecimal longitud) {
        exigirHabilitado("Georef");
        return georef.consultarUbicacion(latitud, longitud);
    }

    private void exigirHabilitado(String api) {
        if (!habilitado) {
            throw new ApiExternaException(api, "las consultas a APIs externas están deshabilitadas (agro.externas.habilitado=false)");
        }
    }
}
