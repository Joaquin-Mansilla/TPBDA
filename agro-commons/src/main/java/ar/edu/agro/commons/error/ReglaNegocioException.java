package ar.edu.agro.commons.error;

import java.util.List;

/** Una regla de negocio no se cumple. Se traduce a HTTP 422 con los motivos en "detalles". */
public class ReglaNegocioException extends RuntimeException {

    private final List<String> motivos;

    public ReglaNegocioException(String message) {
        this(message, List.of());
    }

    public ReglaNegocioException(String message, List<String> motivos) {
        super(message);
        this.motivos = List.copyOf(motivos);
    }

    public List<String> getMotivos() {
        return motivos;
    }
}
