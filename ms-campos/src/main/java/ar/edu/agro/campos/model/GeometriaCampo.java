package ar.edu.agro.campos.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Cálculos sobre el rectángulo de coordenadas que define un establecimiento (T16), sin Spring ni base de datos.
 * Es una aproximación de rectángulo sobre la esfera: alcanza para campos de algunos kilómetros.
 */
public final class GeometriaCampo {

    /** metros por grado de latitud (y de longitud sobre el ecuador) */
    static final double METROS_POR_GRADO = 111_320;

    public record Rectangulo(BigDecimal latitudMin, BigDecimal latitudMax, BigDecimal longitudMin, BigDecimal longitudMax) {
    }

    private GeometriaCampo() {
    }

    /** Hectáreas del rectángulo, redondeadas a 2 decimales. */
    public static BigDecimal superficieHa(BigDecimal latMin, BigDecimal latMax, BigDecimal lonMin, BigDecimal lonMax) {
        double altoM = (latMax.doubleValue() - latMin.doubleValue()) * METROS_POR_GRADO;
        double latitudMedia = Math.toRadians((latMin.doubleValue() + latMax.doubleValue()) / 2);
        double anchoM = (lonMax.doubleValue() - lonMin.doubleValue()) * METROS_POR_GRADO * Math.cos(latitudMedia);
        return BigDecimal.valueOf(altoM * anchoM / 10_000).setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal superficieHa(Rectangulo r) {
        return superficieHa(r.latitudMin(), r.latitudMax(), r.longitudMin(), r.longitudMax());
    }

    /** true si el punto cae dentro del rectángulo, con los bordes incluidos. */
    public static boolean contiene(Rectangulo r, BigDecimal lat, BigDecimal lon) {
        return lat.compareTo(r.latitudMin()) >= 0 && lat.compareTo(r.latitudMax()) <= 0
                && lon.compareTo(r.longitudMin()) >= 0 && lon.compareTo(r.longitudMax()) <= 0;
    }
}
