package ar.edu.agro.commons.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakRealmRoleConverterTest {

    private final KeycloakRealmRoleConverter converter = new KeycloakRealmRoleConverter();

    private Jwt jwt(Map<String, Object> claims) {
        return Jwt.withTokenValue("t").header("alg", "none").subject("user-1").claims(c -> c.putAll(claims)).build();
    }

    @Test
    void convierteRolesDelDominioConPrefijo() {
        Jwt jwt = jwt(Map.of("realm_access", Map.of("roles", List.of("PRODUCTOR", "AGRONOMO"))));
        Collection<GrantedAuthority> auth = converter.convert(jwt);
        assertThat(auth).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_PRODUCTOR", "ROLE_AGRONOMO");
    }

    @Test
    void ignoraRolesQueNoSonDelDominio() {
        Jwt jwt = jwt(Map.of("realm_access", Map.of("roles", List.of("offline_access", "OPERARIO"))));
        assertThat(converter.convert(jwt)).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_OPERARIO");
    }

    @Test
    void sinRealmAccessDevuelveVacio() {
        assertThat(converter.convert(jwt(Map.of("scope", "openid")))).isEmpty();
    }
}
