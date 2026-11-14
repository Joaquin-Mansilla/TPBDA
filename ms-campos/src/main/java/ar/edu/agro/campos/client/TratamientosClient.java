package ar.edu.agro.campos.client;

import ar.edu.agro.campos.config.TratamientosProperties;
import ar.edu.agro.commons.contract.CultivoResumenResponse;
import ar.edu.agro.commons.contract.EvaluacionRequest;
import ar.edu.agro.commons.contract.EvaluacionResponse;
import ar.edu.agro.commons.contract.ResultadoEvaluacion;
import ar.edu.agro.commons.error.RecursoNoEncontradoException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Cliente de ms-campos hacia ms-tratamientos. Reenvía el JWT del usuario (ver ReenvioTokenInterceptor).
 *
 * Dos usos:
 * - obtenerCultivo(): al planificar una campaña (C3 rotación y C4 ventana de siembra).
 * - evaluar(): al registrar una aplicación (reglas T1 a T5, dosis total y costo).
 *
 * Qué hace cada falla (nunca se inventa un dato ni un veredicto):
 * - Tratamientos no responde, responde 5xx o algo inesperado: ApiExternaException (extiende
 *   ServicioNoDisponibleException; llegando a un controller es un 503). Quien llama decide:
 *   al planificar una campaña se rechaza con 503; al evaluar una aplicación queda PENDIENTE_DE_VALIDACION (202).
 * - El cultivo (al planificar) o el cultivo, la plaga o el agroquímico (al evaluar) no existen: RecursoNoEncontradoException (404).
 *   La aplicación no se guarda: es un dato inválido, no una falla de Tratamientos.
 */
@Component
public class TratamientosClient {

    private static final Logger log = LoggerFactory.getLogger(TratamientosClient.class);
    private static final String API = "Tratamientos";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final RestClient restClient;
    private final Ejecutor lecturas;
    private final Ejecutor escrituras;

    public TratamientosClient(@Qualifier("tratamientosRestClient") RestClient restClient,
                              TratamientosProperties props) {
        this.restClient = restClient;
        // Las consultas son seguras de repetir. El pedido de evaluación guarda un registro en Tratamientos: sin reintentos.
        this.lecturas = new Ejecutor(props.reintentosLectura());
        this.escrituras = new Ejecutor(0);
    }

    /**
     * GET /api/v1/cultivos/{id}
     *
     * @throws RecursoNoEncontradoException si el cultivo no existe en el catálogo
     * @throws ApiExternaException          si Tratamientos no responde o devuelve datos inválidos
     */
    public CultivoResumenResponse obtenerCultivo(Long cultivoId) {
        CultivoResumenResponse cultivo = lecturas.correr(() -> {
            try {
                return restClient.get()
                        .uri("/api/v1/cultivos/{id}", cultivoId)
                        .retrieve()
                        .body(CultivoResumenResponse.class);
            } catch (HttpClientErrorException.NotFound e) {
                throw new RecursoNoEncontradoException("El cultivo " + cultivoId + " no existe en el catálogo");
            }
        });

        validar(cultivo);
        return cultivo;
    }

    /**
     * POST /api/v1/evaluaciones
     *
     * @throws RecursoNoEncontradoException si el cultivo, la plaga o el agroquímico no existen en el catálogo
     * @throws ApiExternaException          si Tratamientos no responde, rechaza el pedido o devuelve una decisión inválida
     */
    public EvaluacionResponse evaluar(EvaluacionRequest pedido) {
        EvaluacionResponse evaluacion = escrituras.correr(() -> {
            try {
                return restClient.post()
                        .uri("/api/v1/evaluaciones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(pedido)
                        .retrieve()
                        .body(EvaluacionResponse.class);
            } catch (HttpClientErrorException.NotFound e) {
                // El agroquímico, la plaga o el cultivo del pedido no existen en el catálogo: es un dato inválido del
                // usuario, no una falla de Tratamientos. No se guarda nada y el usuario recibe 404.
                throw new RecursoNoEncontradoException(mensaje(e,
                        "El cultivo, la plaga o el agroquímico del pedido no existen en el catálogo"));
            } catch (HttpClientErrorException.BadRequest e) {
                // Campos armó un pedido que no respeta el contrato: es un error nuestro, no de Tratamientos
                log.error("Tratamientos rechazó el pedido de evaluación por inválido (400): {}",
                        e.getResponseBodyAsString());
                throw e;
            }
        });

        validar(evaluacion);
        return evaluacion;
    }

    /** Mensaje del modelo de error de Tratamientos (campo "message"), o uno por defecto si no se puede leer. */
    private String mensaje(HttpClientErrorException e, String porDefecto) {
        try {
            String mensaje = JSON.readTree(e.getResponseBodyAsString()).path("message").asText(null);
            return mensaje == null || mensaje.isBlank() ? porDefecto : mensaje;
        } catch (Exception ex) {
            return porDefecto;
        }
    }

    private void validar(CultivoResumenResponse cultivo) {
        boolean incompleto = cultivo.id() == null
                || cultivo.familia() == null || cultivo.familia().isBlank()
                || cultivo.diasCiclo() == null || cultivo.diasCiclo() <= 0
                || !esMes(cultivo.mesSiembraDesde())
                || !esMes(cultivo.mesSiembraHasta());
        if (incompleto) {
            throw lecturas.inesperado("cultivo con datos incompletos o fuera de rango: " + cultivo);
        }
    }

    private void validar(EvaluacionResponse evaluacion) {
        if (evaluacion.resultado() == null) {
            throw escrituras.inesperado("evaluación sin resultado");
        }
        if (evaluacion.resultado() == ResultadoEvaluacion.REPROGRAMAR && evaluacion.fechaSugerida() == null) {
            throw escrituras.inesperado("REPROGRAMAR sin fecha sugerida");
        }
    }

    private boolean esMes(Integer mes) {
        return mes != null && mes >= 1 && mes <= 12;
    }

    /** Reutiliza el reintento y la traducción de errores de ClienteApiExterna, con distinta cantidad de reintentos. */
    private static final class Ejecutor extends ClienteApiExterna {

        Ejecutor(int reintentos) {
            super(API, reintentos);
        }

        <T> T correr(Supplier<T> llamada) {
            return ejecutar(llamada);
        }
    }
}
