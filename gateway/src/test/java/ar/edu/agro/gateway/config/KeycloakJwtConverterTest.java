package ar.edu.agro.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakJwtConverterTest {

    private Jwt jwtConClaims(Map<String, Object> claims) {
        return Jwt.withTokenValue("token").header("alg", "none").claims(c -> c.putAll(claims))
                .claim("sub", "11111111-1111-1111-1111-000000000001")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
    }

    @Test
    void convierteLosRolesDelDominioEnAuthoritiesConPrefijoRole() {
        Jwt jwt = jwtConClaims(Map.of("realm_access", Map.of("roles", List.of("PRODUCTOR", "AGRONOMO"))));

        assertThat(KeycloakJwtConverter.authorities(jwt)).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_PRODUCTOR", "ROLE_AGRONOMO");
    }

    @Test
    void ignoraLosRolesQueNoSonDelDominio() {
        Jwt jwt = jwtConClaims(Map.of("realm_access", Map.of("roles", List.of("offline_access", "OPERARIO"))));

        assertThat(KeycloakJwtConverter.authorities(jwt)).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_OPERARIO");
    }

    @Test
    void sinRealmAccessNoHayAuthorities() {
        assertThat(KeycloakJwtConverter.authorities(jwtConClaims(Map.of("scope", "openid")))).isEmpty();
    }
}
