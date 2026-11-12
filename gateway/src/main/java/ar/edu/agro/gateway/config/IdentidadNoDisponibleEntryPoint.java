package ar.edu.agro.gateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.server.BearerTokenServerAuthenticationEntryPoint;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * T12: sin token o con token inválido, 401. Pero si Keycloak no responde y el gateway no tiene sus claves públicas
 * (JWKS) en memoria, Spring Security lo informa como AuthenticationServiceException: no es culpa del token, así que
 * se responde 503 con Retry-After y no 401 (el cliente no debe volver a autenticarse, solo reintentar).
 */
public class IdentidadNoDisponibleEntryPoint implements ServerAuthenticationEntryPoint {

    private static final Logger log = LoggerFactory.getLogger(IdentidadNoDisponibleEntryPoint.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final ServerAuthenticationEntryPoint token401 = new BearerTokenServerAuthenticationEntryPoint();

    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException ex) {
        if (!(ex instanceof AuthenticationServiceException)) {
            return token401.commence(exchange, ex);
        }
        log.error("No se pudo validar el token porque el proveedor de identidad no respondió: {}", ex.getMessage());
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("timestamp", Instant.now().toString());
        cuerpo.put("status", 503);
        cuerpo.put("error", "Service Unavailable");
        cuerpo.put("message", "No se puede validar el token en este momento (proveedor de identidad no disponible); reintente en unos segundos");
        cuerpo.put("path", exchange.getRequest().getPath().value());
        cuerpo.put("details", List.of());
        byte[] bytes;
        try {
            bytes = JSON.writeValueAsBytes(cuerpo);
        } catch (Exception e) {
            return Mono.error(e);
        }
        var response = exchange.getResponse();
        response.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().set("Retry-After", "5");
        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
    }
}
