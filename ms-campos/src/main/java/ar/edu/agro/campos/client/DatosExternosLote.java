package ar.edu.agro.campos.client;

import ar.edu.agro.commons.contract.SueloDto;
import ar.edu.agro.commons.contract.UbicacionDto;

/**
 * Lo que se pudo obtener de las APIs externas para un lote. Si una consulta falló, su dato es null
 * y el lote se crea igual: sin suelo queda en estado SIN_DATOS (y cualquier aplicación va a revisión manual).
 */
public record DatosExternosLote(SueloDto suelo, UbicacionDto ubicacion) {

    public boolean sueloObtenido() {
        return suelo != null;
    }

    public boolean ubicacionObtenida() {
        return ubicacion != null;
    }
}
