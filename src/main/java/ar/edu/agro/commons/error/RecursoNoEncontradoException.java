package ar.edu.agro.commons.error;

/** Se traduce a HTTP 404 en el GlobalExceptionHandler. */
public class RecursoNoEncontradoException extends RuntimeException {
    public RecursoNoEncontradoException(String message) {
        super(message);
    }
}
