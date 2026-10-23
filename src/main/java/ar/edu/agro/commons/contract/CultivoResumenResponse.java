package ar.edu.agro.commons.contract;

/** Lo que Campos necesita del cultivo para planificar una campaña (C3 rotación, C4 ventana y cosecha estimada). */
public record CultivoResumenResponse(
        Long id,
        String nombre,
        String familia,
        Integer diasCiclo,
        Integer mesSiembraDesde,
        Integer mesSiembraHasta) {
}
