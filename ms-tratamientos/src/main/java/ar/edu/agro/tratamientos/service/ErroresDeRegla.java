package ar.edu.agro.tratamientos.service;

import ar.edu.agro.commons.error.ReglaNegocioException;
import java.util.List;

/** Arma la ReglaNegocioException de agro-commons (422); el motivo sale como "CAT: detalle". */
public final class ErroresDeRegla {

    private ErroresDeRegla() {
    }

    public static ReglaNegocioException de(String regla, String detalle) {
        return new ReglaNegocioException(detalle, List.of(regla + ": " + detalle));
    }
}
