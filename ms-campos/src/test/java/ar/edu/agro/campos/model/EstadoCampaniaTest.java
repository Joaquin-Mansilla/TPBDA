package ar.edu.agro.campos.model;

import static ar.edu.agro.campos.model.EstadoCampania.COSECHADA;
import static ar.edu.agro.campos.model.EstadoCampania.PERDIDA;
import static ar.edu.agro.campos.model.EstadoCampania.PLANIFICADA;
import static ar.edu.agro.campos.model.EstadoCampania.SEMBRADA;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** C5: las 16 combinaciones de estado actual y nuevo. */
class EstadoCampaniaTest {

    @ParameterizedTest(name = "{0} -> {1}: {2}")
    @CsvSource({
            "PLANIFICADA, PLANIFICADA, false",
            "PLANIFICADA, SEMBRADA,    true",
            "PLANIFICADA, COSECHADA,   false",
            "PLANIFICADA, PERDIDA,     true",
            "SEMBRADA,    PLANIFICADA, false",
            "SEMBRADA,    SEMBRADA,    false",
            "SEMBRADA,    COSECHADA,   true",
            "SEMBRADA,    PERDIDA,     true",
            "COSECHADA,   PLANIFICADA, false",
            "COSECHADA,   SEMBRADA,    false",
            "COSECHADA,   COSECHADA,   false",
            "COSECHADA,   PERDIDA,     false",
            "PERDIDA,     PLANIFICADA, false",
            "PERDIDA,     SEMBRADA,    false",
            "PERDIDA,     COSECHADA,   false",
            "PERDIDA,     PERDIDA,     false",
    })
    void transiciones(EstadoCampania actual, EstadoCampania nuevo, boolean permitida) {
        assertThat(actual.puedePasarA(nuevo)).isEqualTo(permitida);
    }

    @ParameterizedTest(name = "{0} final: {1}")
    @CsvSource({"PLANIFICADA, false", "SEMBRADA, false", "COSECHADA, true", "PERDIDA, true"})
    void estadosFinales(EstadoCampania estado, boolean esFinal) {
        assertThat(estado.esFinal()).isEqualTo(esFinal);
    }

    @org.junit.jupiter.api.Test
    void soloCuatroEstados() {
        assertThat(EstadoCampania.values()).containsExactly(PLANIFICADA, SEMBRADA, COSECHADA, PERDIDA);
    }
}
