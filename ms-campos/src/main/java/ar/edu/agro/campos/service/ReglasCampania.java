package ar.edu.agro.campos.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Reglas C1 a C4 y C8 (fechas) sin base de datos ni Spring, para probarlas con casos normales, límites y rechazos.
 * (C5, las transiciones de estado, las conoce EstadoCampania.)
 * (C6, la pertenencia, está en AccesoService; L1 y A1 en los servicios de lote y aplicación.)
 */
public final class ReglasCampania {

    public record Rango(LocalDate desde, LocalDate hasta) {
    }

    /** Lo que ocupa una campaña del lote: sus fechas, su cultivo y sus hectáreas (C1 con varios cultivos). */
    public record Ocupacion(LocalDate desde, LocalDate hasta, Long cultivoId, BigDecimal superficieHa) {
    }

    private ReglasCampania() {
    }

    /** true si los dos períodos comparten al menos un día (los extremos cuentan). */
    public static boolean c1Superpone(LocalDate desde, LocalDate hasta, List<Rango> existentes) {
        return existentes.stream().anyMatch(r -> !hasta.isBefore(r.desde()) && !desde.isAfter(r.hasta()));
    }

    /** Las campañas que comparten días con el período dado. */
    public static List<Ocupacion> c1Superpuestas(LocalDate desde, LocalDate hasta, List<Ocupacion> existentes) {
        return existentes.stream().filter(o -> !hasta.isBefore(o.desde()) && !desde.isAfter(o.hasta())).toList();
    }

    /**
     * C1 (con varios cultivos): un lote puede tener campañas al mismo tiempo, por ejemplo 60 ha de soja y 40 ha de
     * maíz, siempre que sean de cultivos distintos y que entre todas no superen la superficie del lote.
     * Se suman todas las campañas que se superponen con la nueva aunque entre ellas no se pisen: es la cuenta
     * conservadora (nunca deja pasar un lote sobrecargado).
     *
     * @return los incumplimientos (vacío si la campaña entra)
     */
    public static List<String> c1Incumplimientos(LocalDate desde, LocalDate hasta, Long cultivoId, BigDecimal superficieHa,
                                                 BigDecimal superficieLote, List<Ocupacion> existentes) {
        List<Ocupacion> superpuestas = c1Superpuestas(desde, hasta, existentes);
        List<String> motivos = new ArrayList<>();
        if (superpuestas.stream().anyMatch(o -> o.cultivoId().equals(cultivoId))) {
            motivos.add("Ya hay una campaña del mismo cultivo en el lote en esas fechas (para más hectáreas, se agranda esa campaña)");
        }
        BigDecimal ocupada = superpuestas.stream().map(Ocupacion::superficieHa).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (!superpuestas.isEmpty() && ocupada.add(superficieHa).compareTo(superficieLote) > 0) {
            motivos.add("Con las campañas que se superponen el lote tendría " + ocupada.add(superficieHa).stripTrailingZeros().toPlainString()
                    + " ha sembradas y tiene " + superficieLote.stripTrailingZeros().toPlainString() + " ha (libres: "
                    + superficieLote.subtract(ocupada).max(BigDecimal.ZERO).stripTrailingZeros().toPlainString() + " ha)");
        }
        return motivos;
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

    /**
     * C9: se puede cosechar desde anticipoDias antes de la cosecha estimada (el cultivo tiene que estar maduro);
     * el mismo día de la estimada y los posteriores también.
     */
    public static boolean c9CosechaATiempo(LocalDate cosecha, LocalDate cosechaEstimada, int anticipoDias) {
        return !cosecha.isBefore(cosechaEstimada.minusDays(anticipoDias));
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
