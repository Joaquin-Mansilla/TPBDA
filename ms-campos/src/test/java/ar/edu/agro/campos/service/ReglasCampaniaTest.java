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
}
