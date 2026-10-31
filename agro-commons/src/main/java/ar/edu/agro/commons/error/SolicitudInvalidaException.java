package ar.edu.agro.commons.error;

/** Se traduce a HTTP 400 en el GlobalExceptionHandler. */
public class SolicitudInvalidaException extends RuntimeException {
    public SolicitudInvalidaException(String message) {
        super(message);
    }
}
