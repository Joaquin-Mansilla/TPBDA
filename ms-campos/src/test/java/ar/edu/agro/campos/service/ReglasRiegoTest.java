package ar.edu.agro.campos.service;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.agro.campos.model.EstadoRiego;
import ar.edu.agro.campos.model.OrigenAgua;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** R1 con un cultivo que pide 25 mm cada 7 días y 2 días de tolerancia. */
class ReglasRiegoTest {

    private static final LocalDate SIEMBRA = LocalDate.of(2026, 11, 1);
    private static final BigDecimal MM = new BigDecimal("25");

    private static ReglasRiego.Resultado calcular(Map<LocalDate, BigDecimal> riegos, Map<LocalDate, BigDecimal> lluvia, LocalDate hoy) {
        return ReglasRiego.calcular(SIEMBRA, riegos, lluvia, 7, MM, 2, hoy);
    }

    private static BigDecimal mm(String valor) {
        return new BigDecimal(valor);
    }

    @Test
    void recienSembradaSinAguaTocaElRiegoDeAsiento() {
        var r = calcular(Map.of(), Map.of(), SIEMBRA);

        assertThat(r.estado()).isEqualTo(EstadoRiego.TOCA_REGAR);
        assertThat(r.origen()).isEqualTo(OrigenAgua.SIEMBRA);
        assertThat(r.proximoRiego()).isEqualTo(SIEMBRA);   // vence el mismo día de la siembra
    }

    @Test
    void unaLluviaQueNoLlegaALosMmNoAlcanzaParaElRiegoDeAsiento() {
        var r = calcular(Map.of(), Map.of(SIEMBRA, mm("6.4")), SIEMBRA);

        assertThat(r.estado()).isEqualTo(EstadoRiego.TOCA_REGAR);
        assertThat(r.mmDesdeUltimaAgua()).isEqualByComparingTo("6.4");
    }

    @Test
    void sinRiegoDeAsientoPasadaLaToleranciaEstaAtrasado() {
        assertThat(calcular(Map.of(), Map.of(), SIEMBRA.plusDays(2)).estado()).isEqualTo(EstadoRiego.TOCA_REGAR);
        assertThat(calcular(Map.of(), Map.of(), SIEMBRA.plusDays(3)).estado()).isEqualTo(EstadoRiego.ATRASADO);
    }

    @Test
    void conElRiegoDeAsientoQuedaAlDiaPorLosDiasDelCultivo() {
        var regada = Map.of(SIEMBRA, mm("25"));

        assertThat(calcular(regada, Map.of(), SIEMBRA).estado()).isEqualTo(EstadoRiego.AL_DIA);
        assertThat(calcular(regada, Map.of(), SIEMBRA.plusDays(6)).estado()).isEqualTo(EstadoRiego.AL_DIA);
        assertThat(calcular(regada, Map.of(), SIEMBRA.plusDays(7)).estado()).isEqualTo(EstadoRiego.TOCA_REGAR);
        assertThat(calcular(regada, Map.of(), SIEMBRA.plusDays(9)).estado()).isEqualTo(EstadoRiego.TOCA_REGAR);
        assertThat(calcular(regada, Map.of(), SIEMBRA.plusDays(10)).estado()).isEqualTo(EstadoRiego.ATRASADO);
    }

    @Test
    void unRiegoCompletoReiniciaLaCuenta() {
        LocalDate riego = SIEMBRA.plusDays(8);

        var r = calcular(Map.of(riego, mm("25")), Map.of(), SIEMBRA.plusDays(10));

        assertThat(r.estado()).isEqualTo(EstadoRiego.AL_DIA);
        assertThat(r.origen()).isEqualTo(OrigenAgua.RIEGO);
        assertThat(r.ultimaAgua()).isEqualTo(riego);
        assertThat(r.diasSinAgua()).isEqualTo(2);
        assertThat(r.proximoRiego()).isEqualTo(riego.plusDays(7));
    }

    @Test
    void unaLluviaQueAlcanzaLosMmCuentaComoRiego() {
        LocalDate lluvia = SIEMBRA.plusDays(8);

        var r = calcular(Map.of(), Map.of(lluvia, mm("30")), SIEMBRA.plusDays(10));

        assertThat(r.estado()).isEqualTo(EstadoRiego.AL_DIA);
        assertThat(r.origen()).isEqualTo(OrigenAgua.LLUVIA);
        assertThat(r.lluviaTotalMm()).isEqualByComparingTo("30");
    }

    @Test
    void unaLluviaChicaNoAlcanzaPeroQuedaAcumulada() {
        var r = calcular(Map.of(), Map.of(SIEMBRA.plusDays(8), mm("10")), SIEMBRA.plusDays(10));

        assertThat(r.estado()).isEqualTo(EstadoRiego.ATRASADO);
        assertThat(r.mmDesdeUltimaAgua()).isEqualByComparingTo("10");
    }

    @Test
    void riegoChicoMasLluviaSumanUnRiegoCompleto() {
        LocalDate dia = SIEMBRA.plusDays(5);

        var r = calcular(Map.of(SIEMBRA.plusDays(4), mm("15")), Map.of(dia, mm("12")), SIEMBRA.plusDays(8));

        assertThat(r.ultimaAgua()).isEqualTo(dia);
        assertThat(r.origen()).isEqualTo(OrigenAgua.LLUVIA);
        assertThat(r.estado()).isEqualTo(EstadoRiego.AL_DIA);
    }

    @Test
    void siElMismoDiaHayRiegoYLluviaElMeritoEsDelRiego() {
        LocalDate dia = SIEMBRA.plusDays(3);

        var r = calcular(Map.of(dia, mm("20")), Map.of(dia, mm("20")), SIEMBRA.plusDays(4));

        assertThat(r.origen()).isEqualTo(OrigenAgua.RIEGO);
    }

    @Test
    void laLluviaAnteriorALaSiembraNoCuenta() {
        var r = calcular(Map.of(), Map.of(SIEMBRA.minusDays(1), mm("80")), SIEMBRA.plusDays(8));

        assertThat(r.origen()).isEqualTo(OrigenAgua.SIEMBRA);
        assertThat(r.lluviaTotalMm()).isEqualByComparingTo("0");
    }
}
