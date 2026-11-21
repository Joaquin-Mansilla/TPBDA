package ar.edu.agro.campos.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Reglas C1 a C4 y C8 (fechas) sin base de datos ni Spring, para probarlas con casos normales, límites y rechazos.
 * (C5, las transiciones de estado, las conoce EstadoCampania.)
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

    /** C4: la siembra real no puede apartarse de la planificada más de toleranciaDias (para adelante o para atrás). */
    public static boolean c4DentroDeTolerancia(LocalDate planificada, LocalDate real, int toleranciaDias) {
        return Math.abs(ChronoUnit.DAYS.between(planificada, real)) <= toleranciaDias;
    }

    /** C8: lo que ya ocurrió (siembra, cosecha, pérdida, monitoreo) no puede tener fecha futura. */
    public static boolean c8NoEsFutura(LocalDate fecha, LocalDate hoy) {
        return !fecha.isAfter(hoy);
    }

    /** C8: la fecha tiene que caer dentro del período, con los dos extremos incluidos. */
    public static boolean c8DentroDe(LocalDate fecha, LocalDate desde, LocalDate hasta) {
        return !fecha.isBefore(desde) && !fecha.isAfter(hasta);
    }
}
