package ar.edu.agro.campos.client;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ReenvioTokenInterceptorTest {

    private MockRestServiceServer server;
    private RestClient restClient;

    @BeforeEach
    void preparar() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("http://tratamientos.test")
                .requestInterceptor(new ReenvioTokenInterceptor());
        server = MockRestServiceServer.bindTo(builder).build();
        restClient = builder.build();
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    private void autenticarConJwt(String valorDelToken) {
        Jwt jwt = Jwt.withTokenValue(valorDelToken)
                .header("alg", "none")
                .claim("sub", "11111111-1111-1111-1111-000000000001")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_PRODUCTOR"))));
    }

    @Test
    void agregaElTokenDelUsuarioComoBearer() {
        autenticarConJwt("token-del-usuario");
        server.expect(requestTo("http://tratamientos.test/algo"))
                .andExpect(header("Authorization", "Bearer token-del-usuario"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        restClient.get().uri("/algo").retrieve().toBodilessEntity();

        server.verify();
    }

    @Test
    void usaElTokenDelUsuarioActualYNoUnoAnterior() {
        autenticarConJwt("token-A");
        server.expect(header("Authorization", "Bearer token-A"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        server.expect(header("Authorization", "Bearer token-B"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        restClient.get().uri("/algo").retrieve().toBodilessEntity();

        autenticarConJwt("token-B");
        restClient.get().uri("/algo").retrieve().toBodilessEntity();

        server.verify();
    }

    @Test
    void sinAutenticacionNoSeEnviaNada() {
        assertThatThrownBy(() -> restClient.get().uri("/algo").retrieve().toBodilessEntity())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No hay un JWT");
        server.verify();
    }

    @Test
    void unaAutenticacionQueNoEsJwtNoSeAcepta() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alguien", "x", List.of()));

        assertThatThrownBy(() -> restClient.get().uri("/algo").retrieve().toBodilessEntity())
                .isInstanceOf(IllegalStateException.class);
    }
}
