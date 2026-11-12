package ar.edu.agro.gateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import reactor.core.publisher.Mono;

/**
 * T12: códigos HTTP del gateway cuando un microservicio no responde. Se ejecuta antes que el handler por defecto.
 *
 *   microservicio caído (conexión rechazada, host inexistente, sin instancia para la ruta) -> 503 Service Unavailable
 *   microservicio que no contesta a tiempo (timeout de conexión o de respuesta)            -> 504 Gateway Timeout
 *   conexión cortada o respuesta ilegible del microservicio                                 -> 502 Bad Gateway
 *
 * Todo lo demás (404 de ruta inexistente, errores de seguridad, etc.) se deja pasar al manejo normal de Spring.
 * El cuerpo usa el mismo formato de error que los microservicios (timestamp, status, error, message, path, details).
 */
@Component
@Order(-2)
public class ErroresGatewayHandler implements ErrorWebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ErroresGatewayHandler.class);

    private final ObjectMapper json;

    public ErroresGatewayHandler(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        HttpStatus status = clasificar(ex);
        if (status == null || exchange.getResponse().isCommitted()) {
            return Mono.error(ex);
        }
        String path = exchange.getRequest().getPath().value();
        log.warn("{} {} -> {} ({})", exchange.getRequest().getMethod(), path, status.value(), resumen(ex));

        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("timestamp", Instant.now().toString());
        cuerpo.put("status", status.value());
        cuerpo.put("error", status.getReasonPhrase());
        cuerpo.put("message", mensaje(status));
        cuerpo.put("path", path);
        cuerpo.put("details", List.of());

        byte[] bytes;
        try {
            bytes = json.writeValueAsBytes(cuerpo);
        } catch (Exception e) {
            return Mono.error(ex);
        }
        var response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        if (status != HttpStatus.BAD_GATEWAY) {
            response.getHeaders().set("Retry-After", "5");
        }
        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
    }

    /** @return el código que corresponde, o null si no es una falla de un microservicio. */
    static HttpStatus clasificar(Throwable error) {
        int profundidad = 0;
        for (Throwable t = error; t != null && profundidad < 10; t = (t.getCause() == t ? null : t.getCause()), profundidad++) {
            if (t instanceof ResponseStatusException rse) {
                int codigo = rse.getStatusCode().value();
                if (codigo == 502 || codigo == 503 || codigo == 504) {
                    return HttpStatus.valueOf(codigo);
                }
            }
            if (esTimeout(t)) {
                return HttpStatus.GATEWAY_TIMEOUT;
            }
            if (t instanceof ConnectException || t instanceof UnknownHostException) {
                return HttpStatus.SERVICE_UNAVAILABLE;
            }
            // conexión cortada a mitad de camino (PrematureCloseException, connection reset, etc.)
            if (t instanceof java.io.IOException || t.getClass().getSimpleName().equals("PrematureCloseException")) {
                return HttpStatus.BAD_GATEWAY;
            }
        }
        return null;
    }

    private static boolean esTimeout(Throwable t) {
        // ReadTimeoutException, WriteTimeoutException y ConnectTimeoutException de Netty se reconocen por el nombre
        return t instanceof TimeoutException || t instanceof SocketTimeoutException
                || t.getClass().getSimpleName().contains("Timeout");
    }

    private static String mensaje(HttpStatus status) {
        return switch (status) {
            case SERVICE_UNAVAILABLE -> "El servicio solicitado no está disponible; reintente en unos segundos";
            case GATEWAY_TIMEOUT -> "El servicio solicitado no respondió a tiempo; reintente en unos segundos";
            default -> "El servicio solicitado respondió de forma inválida";
        };
    }

    private static String resumen(Throwable ex) {
        Throwable raiz = ex;
        while (raiz.getCause() != null && raiz.getCause() != raiz) {
            raiz = raiz.getCause();
        }
        return raiz.getClass().getSimpleName() + ": " + raiz.getMessage();
    }
}
