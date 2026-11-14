package ar.edu.agro.tratamientos.config;

import ar.edu.agro.commons.error.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionTimedOutException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * T12: códigos HTTP ante caídas y timeouts de la base de datos (el GlobalExceptionHandler de agro-commons los
 * devolvería como 500, que significa "bug del servidor" y no "volvé a intentar").
 *   base caída o sin conexiones disponibles -> 503 Service Unavailable
 *   consulta o transacción que excede el tiempo -> 504 Gateway Timeout
 * Siempre con Retry-After. El mensaje no revela datos internos (el detalle va al log).
 * Prioridad máxima por la misma razón que ConflictoHandler: el handler de commons atrapa Exception.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class FallosHandler {

    private static final Logger log = LoggerFactory.getLogger(FallosHandler.class);

    @ExceptionHandler({DataAccessResourceFailureException.class, CannotCreateTransactionException.class})
    public ResponseEntity<ErrorResponse> baseNoDisponible(Exception ex, HttpServletRequest req) {
        log.error("Base de datos no disponible: {}", ex.getMessage());
        return respuesta(HttpStatus.SERVICE_UNAVAILABLE, "La base de datos no está disponible; reintente en unos segundos", req);
    }

    @ExceptionHandler({QueryTimeoutException.class, TransactionTimedOutException.class})
    public ResponseEntity<ErrorResponse> baseLenta(Exception ex, HttpServletRequest req) {
        log.error("Timeout de la base de datos: {}", ex.getMessage());
        return respuesta(HttpStatus.GATEWAY_TIMEOUT, "La base de datos no respondió a tiempo; reintente en unos segundos", req);
    }

    static ResponseEntity<ErrorResponse> respuesta(HttpStatus status, String mensaje, HttpServletRequest req) {
        return ResponseEntity.status(status).header("Retry-After", "5").body(new ErrorResponse(Instant.now(),
                status.value(), status.getReasonPhrase(), mensaje, req.getRequestURI(), List.of()));
    }
}
