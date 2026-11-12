package ar.edu.agro.gateway.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Convierte realm_access.roles del JWT de Keycloak en authorities ROLE_* (versión reactiva).
 * Ignora los roles que no son del dominio (offline_access, uma_authorization, etc.).
 */
final class KeycloakJwtConverter {

    private KeycloakJwtConverter() {
    }

    static Converter<Jwt, Mono<AbstractAuthenticationToken>> build() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(KeycloakJwtConverter::authorities);
        return new ReactiveJwtAuthenticationConverterAdapter(converter);
    }

    static Collection<GrantedAuthority> authorities(Jwt jwt) {
        Object realmAccess = jwt.getClaim("realm_access");
        if (!(realmAccess instanceof Map<?, ?> map) || !(map.get("roles") instanceof Collection<?> roles)) {
            return List.of();
        }
        return roles.stream()
                .map(String::valueOf)
                .filter(Roles.TODOS::contains)
                .<GrantedAuthority>map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .toList();
    }
}
