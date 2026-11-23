package ar.edu.agro.campos.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReglasCampaniaTest {

    private static LocalDate d(String s) {
        return LocalDate.parse(s);
    }

    // C1
    @Test
    void c1_fechasSuperpuestasSeRechazan() {
        var existentes = List.of(new ReglasCampania.Rango(d("2026-11-01"), d("2027-03-31")));
        assertThat(ReglasCampania.c1Superpone(d("2026-12-01"), d("2027-05-01"), existentes)).isTrue();
    }

    @Test
    void c1_fechasSeparadasCumplen() {
        var existentes = List.of(new ReglasCampania.Rango(d("2026-11-01"), d("2027-03-31")));
        assertThat(ReglasCampania.c1Superpone(d("2027-04-01"), d("2027-09-01"), existentes)).isFalse();
    }

    @Test
    void c1_elDiaDeLaCosechaYaEstaOcupado() {
        var existentes = List.of(new ReglasCampania.Rango(d("2026-11-01"), d("2027-03-31")));
        assertThat(ReglasCampania.c1Superpone(d("2027-03-31"), d("2027-08-31"), existentes)).isTrue();
    }

    // C1 con varios cultivos en el lote (lote de 100 ha con 60 ha de soja, cultivo 1)
    private static final BigDecimal LOTE = new BigDecimal("100");
    private static final List<ReglasCampania.Ocupacion> SOJA_60 =
            List.of(new ReglasCampania.Ocupacion(d("2026-11-01"), d("2027-03-31"), 1L, new BigDecimal("60")));

    @Test
    void c1_otroCultivoQueEntraEnLoQueQuedaDelLoteCumple() {
        assertThat(ReglasCampania.c1Incumplimientos(d("2026-11-15"), d("2027-04-24"), 2L, new BigDecimal("40"), LOTE, SOJA_60))
                .isEmpty();
    }

    @Test
    void c1_otroCultivoQueNoEntraSeRechazaYDiceCuantoQuedaLibre() {
        assertThat(ReglasCampania.c1Incumplimientos(d("2026-11-15"), d("2027-04-24"), 2L, new BigDecimal("41"), LOTE, SOJA_60))
                .singleElement().asString().contains("101 ha sembradas").contains("libres: 40 ha");
    }

    @Test
    void c1_elMismoCultivoAlMismoTiempoSeRechazaAunqueEntre() {
        assertThat(ReglasCampania.c1Incumplimientos(d("2026-12-01"), d("2027-04-30"), 1L, new BigDecimal("10"), LOTE, SOJA_60))
                .singleElement().asString().contains("mismo cultivo");
    }

    @Test
    void c1_sinSuperposicionPuedeOcuparTodoElLote() {
        assertThat(ReglasCampania.c1Incumplimientos(d("2027-04-01"), d("2027-09-01"), 1L, LOTE, LOTE, SOJA_60)).isEmpty();
    }

    @Test
    void c1_seSumanTodasLasSuperpuestas() {
        var dos = List.of(SOJA_60.get(0),
                new ReglasCampania.Ocupacion(d("2026-11-15"), d("2027-04-24"), 2L, new BigDecimal("30")));
        assertThat(ReglasCampania.c1Incumplimientos(d("2026-12-01"), d("2027-05-30"), 3L, new BigDecimal("20"), LOTE, dos))
                .singleElement().asString().contains("110 ha sembradas");
    }

    // C9 (estimada 31/03/2027, 10 días de anticipo)
    @Test
    void c9_desde10DiasAntesDeLaEstimadaEnAdelante() {
        LocalDate estimada = d("2027-03-31");
        assertThat(ReglasCampania.c9CosechaATiempo(d("2027-03-20"), estimada, 10)).isFalse();
        assertThat(ReglasCampania.c9CosechaATiempo(d("2027-03-21"), estimada, 10)).isTrue();
        assertThat(ReglasCampania.c9CosechaATiempo(d("2027-03-31"), estimada, 10)).isTrue();
        assertThat(ReglasCampania.c9CosechaATiempo(d("2027-05-01"), estimada, 10)).isTrue();
    }

    // C2
    @Test
    void c2_superficieIgualOMenorCumple() {
        assertThat(ReglasCampania.c2SuperficieValida(new BigDecimal("100"), new BigDecimal("100.00"))).isTrue();
        assertThat(ReglasCampania.c2SuperficieValida(new BigDecimal("99.99"), new BigDecimal("100"))).isTrue();
    }

    @Test
    void c2_superficieMayorSeRechaza() {
        assertThat(ReglasCampania.c2SuperficieValida(new BigDecimal("100.01"), new BigDecimal("100"))).isFalse();
    }

    // C3
    @Test
    void c3_terceraCampaniaSeguidaDeLaMismaFamiliaSeRechaza() {
        assertThat(ReglasCampania.c3RotacionValida("Leguminosa", List.of("Leguminosa", "leguminosa"))).isFalse();
    }

    @Test
    void c3_otraFamiliaOHistorialCortoCumple() {
        assertThat(ReglasCampania.c3RotacionValida("Gramínea", List.of("Leguminosa", "Leguminosa"))).isTrue();
        assertThat(ReglasCampania.c3RotacionValida("Leguminosa", List.of("Leguminosa", "Gramínea"))).isTrue();
        assertThat(ReglasCampania.c3RotacionValida("Leguminosa", List.of("Leguminosa"))).isTrue();
        assertThat(ReglasCampania.c3RotacionValida("Leguminosa", List.of())).isTrue();
    }

    // C4
    @Test
    void c4_dentroDeLaVentanaCumpleIncluidosLosExtremos() {
        assertThat(ReglasCampania.c4DentroDeVentana(d("2026-10-01"), 10, 12)).isTrue();
        assertThat(ReglasCampania.c4DentroDeVentana(d("2026-12-31"), 10, 12)).isTrue();
    }

    @Test
    void c4_fueraDeLaVentanaSeRechaza() {
        assertThat(ReglasCampania.c4DentroDeVentana(d("2026-09-30"), 10, 12)).isFalse();
        assertThat(ReglasCampania.c4DentroDeVentana(d("2027-01-01"), 10, 12)).isFalse();
    }

    @Test
    void c4_ventanaQueCruzaElAnio() {
        assertThat(ReglasCampania.c4DentroDeVentana(d("2027-01-15"), 11, 2)).isTrue();
        assertThat(ReglasCampania.c4DentroDeVentana(d("2026-11-15"), 11, 2)).isTrue();
        assertThat(ReglasCampania.c4DentroDeVentana(d("2026-06-15"), 11, 2)).isFalse();
    }

    // C4: siembra real respecto de la planificada
    @Test
    void c4_laSiembraRealPuedeApartarseHastaLaToleranciaEnLosDosSentidos() {
        assertThat(ReglasCampania.c4DentroDeTolerancia(d("2026-10-15"), d("2026-10-15"), 45)).isTrue();
        assertThat(ReglasCampania.c4DentroDeTolerancia(d("2026-10-15"), d("2026-11-29"), 45)).isTrue();
        assertThat(ReglasCampania.c4DentroDeTolerancia(d("2026-10-15"), d("2026-08-31"), 45)).isTrue();
    }

    @Test
    void c4_unDiaMasQueLaToleranciaSeRechaza() {
        assertThat(ReglasCampania.c4DentroDeTolerancia(d("2026-10-15"), d("2026-11-30"), 45)).isFalse();
        assertThat(ReglasCampania.c4DentroDeTolerancia(d("2026-10-15"), d("2026-08-30"), 45)).isFalse();
        assertThat(ReglasCampania.c4DentroDeTolerancia(d("2026-10-15"), d("2025-10-15"), 45)).isFalse();
    }

    // C8
    @Test
    void c8_hoyNoEsFuturoYMananaSi() {
        assertThat(ReglasCampania.c8NoEsFutura(d("2026-11-21"), d("2026-11-21"))).isTrue();
        assertThat(ReglasCampania.c8NoEsFutura(d("2026-11-20"), d("2026-11-21"))).isTrue();
        assertThat(ReglasCampania.c8NoEsFutura(d("2026-11-22"), d("2026-11-21"))).isFalse();
    }

    @Test
    void c8_dentroDelPeriodoConLosExtremosIncluidos() {
        assertThat(ReglasCampania.c8DentroDe(d("2026-11-01"), d("2026-11-01"), d("2027-03-31"))).isTrue();
        assertThat(ReglasCampania.c8DentroDe(d("2027-03-31"), d("2026-11-01"), d("2027-03-31"))).isTrue();
        assertThat(ReglasCampania.c8DentroDe(d("2027-01-10"), d("2026-11-01"), d("2027-03-31"))).isTrue();
    }

    @Test
    void c8_fueraDelPeriodoSeRechaza() {
        assertThat(ReglasCampania.c8DentroDe(d("2026-10-31"), d("2026-11-01"), d("2027-03-31"))).isFalse();
        assertThat(ReglasCampania.c8DentroDe(d("2027-04-01"), d("2026-11-01"), d("2027-03-31"))).isFalse();
    }
}
