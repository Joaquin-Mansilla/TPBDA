package ar.edu.agro.commons.contract;

/** Ubicación administrativa del lote (viene de Georef). Puede venir en null si Georef falló. */
public record UbicacionDto(String provincia, String departamento, String municipio) {

    /** Para T5 hace falta al menos la provincia. */
    public boolean tieneProvincia() {
        return provincia != null && !provincia.isBlank();
    }
}
