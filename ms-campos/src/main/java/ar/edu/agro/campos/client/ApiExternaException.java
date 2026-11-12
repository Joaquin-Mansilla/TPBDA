package ar.edu.agro.campos.client;

import ar.edu.agro.commons.error.ServicioNoDisponibleException;

/**
 * Falla al consultar una API externa: no respondió, devolvió un error o devolvió algo inesperado
 * (formato o valores fuera de rango). Nunca se inventa un dato: el que llama decide qué hacer.
 *
 * Extiende ServicioNoDisponibleException de agro-commons, así que si llega a un controller
 * (por ejemplo en POST /lotes/{id}/suelo/refrescar) la traduce FallosHandler (T12): 503 si la API está caída o respondió
 * mal, 504 si no contestó a tiempo (esTimeout). 
 */
public class ApiExternaException extends ServicioNoDisponibleException {

    private final String api;
    private boolean timeout;

    public ApiExternaException(String api, String motivo) {
        super(api + " no disponible: " + motivo);
        this.api = api;
    }

    public ApiExternaException(String api, String motivo, Throwable causa) {
        this(api, motivo);
        initCause(causa);
    }

    /** Marca la falla como timeout (la API no contestó a tiempo): se responde 504 en lugar de 503. */
    public ApiExternaException conTimeout() {
        this.timeout = true;
        return this;
    }

    /** true si la API no contestó dentro del tiempo permitido; false si estaba caída o respondió mal. */
    public boolean esTimeout() {
        return timeout;
    }

    public String getApi() {
        return api;
    }
}
