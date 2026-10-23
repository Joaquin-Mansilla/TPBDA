package ar.edu.agro.commons.security;

import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

/**
 * Arma el converter para el Resource Server (MVC). El nombre de la autenticación es el sub del token,
 * que es lo que se compara con el dueño del establecimiento (C6).
 */
public final class KeycloakJwtAuthenticationConverter {

    private KeycloakJwtAuthenticationConverter() {
    }

    public static JwtAuthenticationConverter build() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRoleConverter());
        return converter;
    }
}
