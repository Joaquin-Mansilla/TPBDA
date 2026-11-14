package ar.edu.agro.tratamientos.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReglasEvaluacionTest {

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    // T1
    @Test
    void t1_habilitadoCumple() {
        assertThat(ReglasEvaluacion.t1Habilitacion(true)).isEmpty();
    }

    @Test
    void t1_noHabilitadoRechaza() {
        assertThat(ReglasEvaluacion.t1Habilitacion(false)).get().satisfies(m -> assertThat(m.regla()).isEqualTo("T1"));
    }

    // T2
    @Test
    void t2_dosisDentroDelRangoCumple() {
        assertThat(ReglasEvaluacion.t2Dosis(bd("1.0"), bd("0.5"), bd("1.5"), bd("1.00"))).isEmpty();
    }

    @Test
    void t2_dosisIgualALaMaximaAjustadaCumple() {
        // arenoso: 1.5 * 0.8 = 1.2
        assertThat(ReglasEvaluacion.t2Dosis(bd("1.2"), bd("0.5"), bd("1.5"), bd("0.80"))).isEmpty();
    }

    @Test
    void t2_dosisSobreLaMaximaAjustadaPorSueloRechaza() {
        // 1.3 sería válida en suelo franco pero no en arenoso
        assertThat(ReglasEvaluacion.t2Dosis(bd("1.3"), bd("0.5"), bd("1.5"), bd("1.00"))).isEmpty();
        assertThat(ReglasEvaluacion.t2Dosis(bd("1.3"), bd("0.5"), bd("1.5"), bd("0.80"))).get().satisfies(m -> assertThat(m.regla()).isEqualTo("T2"));
    }

    @Test
    void t2_dosisBajoLaMinimaRechaza() {
        assertThat(ReglasEvaluacion.t2Dosis(bd("0.4"), bd("0.5"), bd("1.5"), bd("1.00"))).isPresent();
    }

    // T3
    @Test
    void t3_sinAplicacionPreviaCumple() {
        assertThat(ReglasEvaluacion.t3Intervalo(LocalDate.of(2026, 11, 20), null, 14)).isEmpty();
    }

    @Test
    void t3_antesDelIntervaloSugiereFecha() {
        var sugerida = ReglasEvaluacion.t3Intervalo(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 5), 14);
        assertThat(sugerida).contains(LocalDate.of(2026, 11, 19));
    }

    @Test
    void t3_justoAlCumplirElIntervaloCumple() {
        assertThat(ReglasEvaluacion.t3Intervalo(LocalDate.of(2026, 11, 19), LocalDate.of(2026, 11, 5), 14)).isEmpty();
    }

    // T4
    @Test
    void t4_carenciaTerminaAntesDeLaCosechaCumple() {
        assertThat(ReglasEvaluacion.t4Carencia(LocalDate.of(2026, 12, 1), 21, LocalDate.of(2027, 3, 31))).isEmpty();
    }

    @Test
    void t4_carenciaTerminaElDiaDeLaCosechaCumple() {
        assertThat(ReglasEvaluacion.t4Carencia(LocalDate.of(2027, 3, 10), 21, LocalDate.of(2027, 3, 31))).isEmpty();
    }

    @Test
    void t4_carenciaTerminaDespuesDeLaCosechaRechaza() {
        assertThat(ReglasEvaluacion.t4Carencia(LocalDate.of(2027, 3, 11), 21, LocalDate.of(2027, 3, 31)))
                .get().satisfies(m -> assertThat(m.regla()).isEqualTo("T4"));
    }

    // T5
    @Test
    void t5_provinciaRestringidaRechaza() {
        var r = List.of(new ReglasEvaluacion.Restriccion("Córdoba", null));
        assertThat(ReglasEvaluacion.t5Restriccion("córdoba", "Marcos Juárez", r)).get().satisfies(m -> assertThat(m.regla()).isEqualTo("T5"));
    }

    @Test
    void t5_otraProvinciaCumple() {
        var r = List.of(new ReglasEvaluacion.Restriccion("Córdoba", null));
        assertThat(ReglasEvaluacion.t5Restriccion("Santa Fe", "Rosario", r)).isEmpty();
    }

    @Test
    void t5_restriccionPorDepartamentoSoloAfectaAEseDepartamento() {
        var r = List.of(new ReglasEvaluacion.Restriccion("Córdoba", "Río Cuarto"));
        assertThat(ReglasEvaluacion.t5Restriccion("Córdoba", "Río Cuarto", r)).isPresent();
        assertThat(ReglasEvaluacion.t5Restriccion("Córdoba", "Marcos Juárez", r)).isEmpty();
    }

    // costo
    @Test
    void costo_redondeaLosEnvasesHaciaArriba() {
        // 1.0 l/ha * 100 ha = 100 l ; envase de 20 l -> 5 envases ; 5 * 150000
        var c = ReglasEvaluacion.calcularCosto(bd("1.0"), bd("100"), bd("20"), bd("150000"));
        assertThat(c.dosisTotal()).isEqualByComparingTo("100");
        assertThat(c.envases()).isEqualTo(5);
        assertThat(c.costo()).isEqualByComparingTo("750000");
        // 105 l -> 6 envases
        assertThat(ReglasEvaluacion.calcularCosto(bd("1.05"), bd("100"), bd("20"), bd("150000")).envases()).isEqualTo(6);
    }
}
