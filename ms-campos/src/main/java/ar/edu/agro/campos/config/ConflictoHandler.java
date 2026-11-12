package ar.edu.agro.campos.config;

import ar.edu.agro.commons.error.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Errores propios de ms-campos que agro-commons no cubre.
 *
 * Tiene la máxima prioridad a propósito: el GlobalExceptionHandler de agro-commons atrapa Exception (500) y, si
 * ese consejo se consulta primero, ninguno de estos handlers llegaría a usarse.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ConflictoHandler {

    /** Bloqueo optimista (columna version): si dos usuarios modifican a la vez, el segundo recibe 409 en vez de pisar el cambio. */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> conflicto(OptimisticLockingFailureException ex, HttpServletRequest req) {
        return respuesta(HttpStatus.CONFLICT,
                "El recurso fue modificado por otro usuario; volvé a consultarlo e intentá de nuevo", req, List.of());
    }

    /** Un parámetro con un valor que no corresponde (por ejemplo ?estado=XXX): 400 y no 500. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> parametroInvalido(MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        return respuesta(HttpStatus.BAD_REQUEST, "El valor del parámetro '" + ex.getName() + "' no es válido", req,
                List.of(ex.getName() + ": " + ex.getValue()));
    }

    private ResponseEntity<ErrorResponse> respuesta(HttpStatus status, String mensaje, HttpServletRequest req,
                                                    List<String> detalles) {
        return ResponseEntity.status(status).body(new ErrorResponse(Instant.now(), status.value(),
                status.getReasonPhrase(), mensaje, req.getRequestURI(), detalles));
    }
}
