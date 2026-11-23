package ar.edu.agro.campos.service;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.agro.commons.security.Roles;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

/** El nombre para mostrar sale de preferred_username y name del token de Keycloak. */
class UsuarioTest {

    private static Jwt.Builder token() {
        return Jwt.withTokenValue("t").header("alg", "none").subject("22222222-2222-2222-2222-000000000001")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60))
                .claim("realm_access", Map.of("roles", List.of(Roles.OPERARIO)));
    }

    @Test
    void conUsuarioYNombreArmaLosDos() {
        Usuario u = Usuario.de(token().claim("preferred_username", "operario1").claim("name", "Oscar Operario").build());

        assertThat(u.nombre()).isEqualTo("operario1 (Oscar Operario)");
        assertThat(u.sub()).isEqualTo("22222222-2222-2222-2222-000000000001");
        assertThat(u.esOperario()).isTrue();
    }

    @Test
    void conUnoSoloUsaEseYSinNingunoQuedaNull() {
        assertThat(Usuario.de(token().claim("preferred_username", "operario1").build()).nombre()).isEqualTo("operario1");
        assertThat(Usuario.de(token().claim("name", "Oscar Operario").build()).nombre()).isEqualTo("Oscar Operario");
        assertThat(Usuario.de(token().build()).nombre()).isNull();
    }

    @Test
    void seRecortaA100Caracteres() {
        assertThat(Usuario.nombreParaMostrar("u", "x".repeat(200))).hasSize(100);
    }
}
