package ar.edu.agro.campos.model;

/** Resultado de la regla R1 para una campaña. No se guarda: se calcula cada vez que se consulta. */
public enum EstadoRiego {
    /** el cultivo se hace a secano */
    NO_REQUIERE,
    /** la campaña no está SEMBRADA: no hay nada que regar */
    NO_SEMBRADA,
    /** recibió agua (riego o lluvia) hace menos de diasEntreRiegos días */
    AL_DIA,
    /** se cumplieron los días: hay que regar (dentro de la tolerancia) */
    TOCA_REGAR,
    /** se pasó de los días más la tolerancia */
    ATRASADO
}
