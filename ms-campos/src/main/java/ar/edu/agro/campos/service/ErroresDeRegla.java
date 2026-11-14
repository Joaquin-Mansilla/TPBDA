package ar.edu.agro.campos.service;

import ar.edu.agro.commons.contract.MotivoDto;
import ar.edu.agro.commons.error.ReglaNegocioException;
import java.util.ArrayList;
import java.util.List;

/**
 * Arma la ReglaNegocioException de agro-commons (422) a partir de reglas incumplidas.
 * ReglaNegocioException lleva los motivos como texto, así que cada uno sale como "C5: detalle".
 */
public final class ErroresDeRegla {

    private ErroresDeRegla() {
    }

    /** Una sola regla incumplida. */
    public static ReglaNegocioException de(String regla, String detalle) {
        return new ReglaNegocioException(detalle, List.of(regla + ": " + detalle));
    }

    /** Varias reglas incumplidas juntas. */
    public static ReglaNegocioException de(String mensaje, List<MotivoDto> motivos) {
        List<String> textos = new ArrayList<>();
        for (MotivoDto motivo : motivos) {
            textos.add(motivo.regla() + ": " + motivo.detalle());
        }
        return new ReglaNegocioException(mensaje, textos);
    }
}
