package ar.edu.agro.campos.service;

import ar.edu.agro.campos.model.EstadoCampania;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Reglas C1 a C5 sin base de datos ni Spring, para probarlas con casos normales, límites y rechazos.
 * (C6, la pertenencia, está en AccesoService; L1 y A1 en los servicios de lote y aplicación.)
 */
public final class ReglasCampania {

    public record Rango(LocalDate desde, LocalDate hasta) {
    }

    private ReglasCampania() {
    }

    /** C1: una campaña no puede superponerse en fechas con otra del mismo lote. */
    public static boolean c1Superpone(LocalDate desde, LocalDate hasta, List<Rango> existentes) {
        return existentes.stream().anyMatch(r -> !hasta.isBefore(r.desde()) && !desde.isAfter(r.hasta()));
    }

    /** C2: la superficie sembrada (o tratada) no puede superar la del lote (o la sembrada). */
    public static boolean c2SuperficieValida(BigDecimal superficie, BigDecimal maxima) {
        return superficie.compareTo(maxima) <= 0;
    }

    /** C3: rotación. No se permite una tercera campaña seguida de la misma familia en el lote. */
    public static boolean c3RotacionValida(String familiaNueva, List<String> familiasAnterioresMasRecientePrimero) {
        if (familiasAnterioresMasRecientePrimero.size() < 2) {
            return true;
        }
        return !(familiasAnterioresMasRecientePrimero.get(0).equalsIgnoreCase(familiaNueva)
                && familiasAnterioresMasRecientePrimero.get(1).equalsIgnoreCase(familiaNueva));
    }

    /** C4: la fecha de siembra debe estar dentro de la ventana del cultivo (los meses pueden cruzar el año). */
    public static boolean c4DentroDeVentana(LocalDate fechaSiembra, int mesDesde, int mesHasta) {
        int mes = fechaSiembra.getMonthValue();
        return mesDesde <= mesHasta ? (mes >= mesDesde && mes <= mesHasta) : (mes >= mesDesde || mes <= mesHasta);
    }

    /** C5: transiciones de estado permitidas de una campaña. */
    public static boolean c5TransicionValida(EstadoCampania actual, EstadoCampania nuevo) {
        return switch (actual) {
            case PLANIFICADA -> nuevo == EstadoCampania.SEMBRADA || nuevo == EstadoCampania.PERDIDA;
            case SEMBRADA -> nuevo == EstadoCampania.COSECHADA || nuevo == EstadoCampania.PERDIDA;
            case COSECHADA, PERDIDA -> false;
        };
    }
}
