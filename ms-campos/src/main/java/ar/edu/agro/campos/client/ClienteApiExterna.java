package ar.edu.agro.campos.client;

import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Lógica común de los clientes de APIs externas: un reintento corto ante fallas transitorias
 * (timeout, conexión caída o error 5xx) y traducción de todo error a ApiExternaException.
 * Un error 4xx no se reintenta: repetir el mismo pedido da el mismo resultado
 * (y con SoilGrids un 429 significa que se superó el límite de consultas por minuto).
 */
abstract class ClienteApiExterna {

    private static final Logger log = LoggerFactory.getLogger(ClienteApiExterna.class);

    private final String api;
    private final int reintentos;

    ClienteApiExterna(String api, int reintentos) {
        this.api = api;
        this.reintentos = reintentos;
    }

    protected <T> T ejecutar(Supplier<T> llamada) {
        int maxIntentos = 1 + reintentos;
        ApiExternaException ultimoError = null;

        for (int intento = 1; intento <= maxIntentos; intento++) {
            try {
                T respuesta = llamada.get();
                if (respuesta == null) {
                    throw new ApiExternaException(api, "respuesta vacía");
                }
                return respuesta;
            } catch (ApiExternaException e) {
                // respuesta vacía u otra falla ya traducida: no tiene sentido reintentar
                throw e;
            } catch (RestClientResponseException e) {
                int status = e.getStatusCode().value();
                if (status >= 500) {
                    ultimoError = new ApiExternaException(api, "error " + status + " del servidor", e);
                } else {
                    throw new ApiExternaException(api, "respondió " + status, e);
                }
            } catch (ResourceAccessException e) {
                ultimoError = new ApiExternaException(api, "no respondió (" + e.getMessage() + ")", e);
                if (esTimeout(e)) {
                    ultimoError.conTimeout();   // T12: se responderá 504 y no 503
                }
            } catch (RestClientException e) {
                // por ejemplo, un JSON que no se puede leer
                throw new ApiExternaException(api, "respuesta con formato inesperado", e);
            }

            if (intento < maxIntentos) {
                log.warn("{}: intento {} de {} falló ({}), reintentando", api, intento, maxIntentos,
                        ultimoError.getMessage());
            }
        }
        throw ultimoError;
    }

    /**
     * true si la causa es un timeout de conexión o de lectura (SocketTimeoutException o TimeoutException).
     * Una conexión rechazada (servicio caído) NO es timeout.
     */
    static boolean esTimeout(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof java.net.SocketTimeoutException || t instanceof java.util.concurrent.TimeoutException) {
                return true;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return false;
    }

    protected ApiExternaException inesperado(String detalle) {
        log.warn("{}: respuesta inesperada, se descarta: {}", api, detalle);
        return new ApiExternaException(api, "respuesta inesperada (" + detalle + ")");
    }
}
