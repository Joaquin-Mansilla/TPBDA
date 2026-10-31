package ar.edu.agro.commons.contract;

/** Motivo por el que una regla no se cumplió, por ejemplo regla "T4" y su detalle. */
public record MotivoDto(String regla, String detalle) {
}
