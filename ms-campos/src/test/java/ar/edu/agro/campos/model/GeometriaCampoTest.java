package ar.edu.agro.campos.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Superficie del rectángulo del establecimiento y puntos adentro, en el borde y afuera (T16). */
class GeometriaCampoTest {

    private static BigDecimal b(String s) {
        return new BigDecimal(s);
    }

    private final GeometriaCampo.Rectangulo laEsperanza =
            new GeometriaCampo.Rectangulo(b("-32.73"), b("-32.69"), b("-62.15"), b("-62.09"));

    @Test
    void unCentesimoDeGradoPorLadoEnLaLatitudDeCordobaSonUnas104Hectareas() {
        BigDecimal ha = GeometriaCampo.superficieHa(b("-32.705"), b("-32.695"), b("-62.105"), b("-62.095"));

        assertThat(ha.doubleValue()).isCloseTo(104.3, within(0.5));
        assertThat(ha.scale()).isEqualTo(2);
    }

    @Test
    void elRectanguloDeLaEsperanzaTieneUnas2500Hectareas() {
        assertThat(GeometriaCampo.superficieHa(laEsperanza).doubleValue()).isCloseTo(2502, within(5.0));
    }

    @Test
    void lejosDelEcuadorElMismoRectanguloEnGradosOcupaMenos() {
        BigDecimal ecuador = GeometriaCampo.superficieHa(b("0"), b("0.01"), b("0"), b("0.01"));
        BigDecimal cordoba = GeometriaCampo.superficieHa(b("-32.71"), b("-32.70"), b("0"), b("0.01"));

        assertThat(cordoba).isLessThan(ecuador);
    }

    @Test
    void unPuntoAdentroOEnElBordeEstaContenido() {
        assertThat(GeometriaCampo.contiene(laEsperanza, b("-32.70"), b("-62.10"))).isTrue();
        assertThat(GeometriaCampo.contiene(laEsperanza, b("-32.73"), b("-62.15"))).isTrue();
        assertThat(GeometriaCampo.contiene(laEsperanza, b("-32.69"), b("-62.09"))).isTrue();
    }

    @Test
    void unPuntoAfueraNoEstaContenido() {
        assertThat(GeometriaCampo.contiene(laEsperanza, b("-32.7301"), b("-62.10"))).isFalse();
        assertThat(GeometriaCampo.contiene(laEsperanza, b("-32.70"), b("-62.0899"))).isFalse();
        assertThat(GeometriaCampo.contiene(laEsperanza, b("-33.50"), b("-62.10"))).isFalse();
    }
}
