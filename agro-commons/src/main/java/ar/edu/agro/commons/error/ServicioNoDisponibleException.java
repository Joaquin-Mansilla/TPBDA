package ar.edu.agro.commons.error;

/** Se traduce a HTTP 503 en el GlobalExceptionHandler. */
public class ServicioNoDisponibleException extends RuntimeException {
    public ServicioNoDisponibleException(String message) {
        super(message);
    }
}
