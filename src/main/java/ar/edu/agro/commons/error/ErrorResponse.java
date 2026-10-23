package ar.edu.agro.commons.error;

import java.time.Instant;
import java.util.List;

/** Modelo de error común a todos los servicios. */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<String> detalles) {
}
