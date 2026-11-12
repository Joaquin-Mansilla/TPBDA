package ar.edu.agro.campos.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

class ConflictoHandlerTest {

    private final ConflictoHandler handler = new ConflictoHandler();
    private final MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/aplicaciones/1/autorizacion");

    @Test
    void dosAgronomosQueResuelvenLaMismaPendienteRecibenUn409() {
        var r = handler.conflicto(new OptimisticLockingFailureException("version"), req);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(r.getBody().status()).isEqualTo(409);
        assertThat(r.getBody().path()).isEqualTo("/api/v1/aplicaciones/1/autorizacion");
    }

    @Test
    void unEstadoInvalidoEnElFiltroEs400YNo500() {
        var ex = new MethodArgumentTypeMismatchException("XXX", Enum.class, "estado", null, null);

        var r = handler.parametroInvalido(ex, req);

        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(r.getBody().message()).contains("estado");
    }
}
