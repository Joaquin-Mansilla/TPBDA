package ar.edu.agro.commons.contract;

/** Resultado de la decisión de Tratamientos sobre una aplicación. */
public enum ResultadoEvaluacion {
    /** Se cumplen todas las reglas: Campos registra la aplicación. */
    VIABLE,
    /** Solo falla el intervalo mínimo (T3): Campos informa la fecha sugerida. */
    REPROGRAMAR,
    /** Falla alguna otra regla: Campos informa los motivos. */
    NO_RECOMENDADO,
    /** Faltan datos de suelo o ubicación: queda pendiente del AGRONOMO. */
    REVISION_MANUAL
}
