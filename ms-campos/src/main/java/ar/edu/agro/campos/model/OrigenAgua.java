package ar.edu.agro.campos.model;

/** De dónde salió la última "agua completa" que recibió el cultivo (R1). */
public enum OrigenAgua {
    /** todavía no recibió agua suficiente: se cuenta desde la siembra */
    SIEMBRA,
    RIEGO,
    LLUVIA
}
