package ar.edu.agro.campos.client;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.agro.commons.contract.ClaseTextura;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ClasificadorTexturaTest {

    private final ClasificadorTextura clasificador = new ClasificadorTextura(PropiedadesDePrueba.con(1));

    @Test
    void arenaEnElUmbralEsArenoso() {
        assertThat(clasificador.clasificar(new BigDecimal("50.0"), new BigDecimal("10.0")))
                .isEqualTo(ClaseTextura.ARENOSO);
    }

    @Test
    void arcillaEnElUmbralEsArcilloso() {
        assertThat(clasificador.clasificar(new BigDecimal("20.0"), new BigDecimal("35.0")))
                .isEqualTo(ClaseTextura.ARCILLOSO);
    }

    @Test
    void porDebajoDeLosDosUmbralesEsFranco() {
        assertThat(clasificador.clasificar(new BigDecimal("49.9"), new BigDecimal("34.9")))
                .isEqualTo(ClaseTextura.FRANCO);
    }

    @Test
    void siSeCumplenLosDosUmbralesGanaArenoso() {
        assertThat(clasificador.clasificar(new BigDecimal("55.0"), new BigDecimal("35.0")))
                .isEqualTo(ClaseTextura.ARENOSO);
    }
}
