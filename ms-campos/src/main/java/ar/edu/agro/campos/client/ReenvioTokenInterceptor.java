package ar.edu.agro.campos.client;

import java.io.IOException;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Reenvía a Tratamientos el JWT del usuario que originó el pedido (Authorization: Bearer ...), así la
 * evaluación se hace en nombre de quien la pidió y no con una credencial genérica de servicio.
 *
 * El token se toma del SecurityContext del hilo actual, así que el cliente tiene que usarse dentro del
 * pedido HTTP del usuario (no desde un @Async ni un @Scheduled: ahí no hay token que reenviar).
 * El token nunca se registra en el log.
 */
public class ReenvioTokenInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();

        if (!(autenticacion instanceof JwtAuthenticationToken jwtAutenticacion)) {
            // Es un error de programación, no una falla de Tratamientos: no se manda el pedido sin identidad.
            throw new IllegalStateException(
                    "No hay un JWT en el contexto de seguridad para reenviar a Tratamientos. "
                            + "El cliente solo puede usarse dentro del pedido HTTP de un usuario autenticado.");
        }

        request.getHeaders().setBearerAuth(jwtAutenticacion.getToken().getTokenValue());
        return execution.execute(request, body);
    }
}
