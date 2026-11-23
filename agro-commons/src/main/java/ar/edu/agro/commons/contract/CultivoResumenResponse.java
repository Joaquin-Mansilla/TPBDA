package ar.edu.agro.commons.contract;

import java.math.BigDecimal;

/**
 * Lo que Campos necesita del cultivo para planificar una campaña (C3 rotación, C4 ventana y cosecha estimada)
 * y para saber si toca regarla (R1).
 *
 * diasEntreRiegos y mmPorRiego van juntos: los dos null significa que el cultivo se hace a secano (no se riega).
 */
public record CultivoResumenResponse(
        Long id,
        String nombre,
        String familia,
        Integer diasCiclo,
        Integer mesSiembraDesde,
        Integer mesSiembraHasta,
        Integer diasEntreRiegos,
        BigDecimal mmPorRiego) {

    /** Cultivo a secano (sin datos de riego). */
    public CultivoResumenResponse(Long id, String nombre, String familia, Integer diasCiclo,
                                  Integer mesSiembraDesde, Integer mesSiembraHasta) {
        this(id, nombre, familia, diasCiclo, mesSiembraDesde, mesSiembraHasta, null, null);
    }

    /** true si el cultivo tiene definida una frecuencia de riego. */
    public boolean requiereRiego() {
        return diasEntreRiegos != null && mmPorRiego != null;
    }
}
