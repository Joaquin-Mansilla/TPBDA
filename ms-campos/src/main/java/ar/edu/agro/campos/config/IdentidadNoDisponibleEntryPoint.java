package ar.edu.agro.campos.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;

/**
 * T12: qué responde el servicio cuando no puede validar el token.
 *
 * - Token ausente, mal formado, vencido o con firma inválida: 401 (lo resuelve BearerTokenAuthenticationEntryPoint).
 * - Keycloak no responde y el servicio no tiene sus claves públicas (JWKS) en memoria: Spring Security lo informa como
 *   AuthenticationServiceException. No es culpa del token, así que se responde 503 con Retry-After: el cliente no debe
 *   volver a autenticarse, solo reintentar. Mientras las claves estén en caché, los tokens siguen validándose sin Keycloak.
 */
public class IdentidadNoDisponibleEntryPoint implements AuthenticationEntryPoint {

    private static final Logger log = LoggerFactory.getLogger(IdentidadNoDisponibleEntryPoint.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final AuthenticationEntryPoint token401 = new BearerTokenAuthenticationEntryPoint();

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException, ServletException {
        if (!(ex instanceof AuthenticationServiceException)) {
            token401.commence(request, response, ex);
            return;
        }
        log.error("No se pudo validar el token porque el proveedor de identidad no respondió: {}", ex.getMessage());
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("timestamp", Instant.now().toString());
        cuerpo.put("status", 503);
        cuerpo.put("error", "Service Unavailable");
        cuerpo.put("message", "No se puede validar el token en este momento (proveedor de identidad no disponible); reintente en unos segundos");
        cuerpo.put("path", request.getRequestURI());
        cuerpo.put("details", List.of());
        response.setStatus(503);
        response.setHeader("Retry-After", "5");
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(JSON.writeValueAsString(cuerpo));
    }
}
