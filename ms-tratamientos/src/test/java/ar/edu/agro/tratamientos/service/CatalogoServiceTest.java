package ar.edu.agro.tratamientos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.agro.commons.error.ReglaNegocioException;
import ar.edu.agro.tratamientos.dto.CultivoRequest;
import ar.edu.agro.tratamientos.model.Cultivo;
import ar.edu.agro.tratamientos.repository.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Cultivos con y sin riego (R1): diasEntreRiegos y mmPorRiego van juntos. */
class CatalogoServiceTest {

    private CultivoRepository cultivos;
    private CatalogoService servicio;

    @BeforeEach
    void preparar() {
        cultivos = mock(CultivoRepository.class);
        when(cultivos.save(any(Cultivo.class))).thenAnswer(i -> {
            Cultivo c = i.getArgument(0);
            c.setId(9L);
            return c;
        });
        servicio = new CatalogoService(cultivos, mock(PlagaRepository.class), mock(AgroquimicoRepository.class),
                mock(HabilitacionRepository.class), mock(AjusteSueloRepository.class),
                mock(RestriccionTerritorialRepository.class));
    }

    @Test
    void unCultivoConRiegoDevuelveSuFrecuencia() {
        var r = servicio.crearCultivo(new CultivoRequest("Girasol", "Asterácea", 120, 9, 12, 6, new BigDecimal("20")));

        assertThat(r.requiereRiego()).isTrue();
        assertThat(r.diasEntreRiegos()).isEqualTo(6);
        assertThat(r.mmPorRiego()).isEqualByComparingTo("20");
    }

    @Test
    void unCultivoSinDatosDeRiegoEsASecano() {
        var r = servicio.crearCultivo(new CultivoRequest("Cebada", "Gramínea", 170, 5, 7, null, null));

        assertThat(r.requiereRiego()).isFalse();
    }

    @Test
    void laFrecuenciaSinLaLaminaSeRechaza() {
        assertThatThrownBy(() -> servicio.crearCultivo(new CultivoRequest("Girasol", "Asterácea", 120, 9, 12, 6, null)))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("van juntos");
        verify(cultivos, never()).save(any());
    }
}
