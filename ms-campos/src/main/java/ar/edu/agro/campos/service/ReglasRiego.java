package ar.edu.agro.campos.service;

import ar.edu.agro.campos.model.EstadoRiego;
import ar.edu.agro.campos.model.OrigenAgua;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * Regla R1 (riego) sin base de datos ni Spring, para probarla con casos normales y límites.
 *
 * El cultivo necesita mmPorRiego mm de agua cada diasEntreRiegos días, y da lo mismo si viene de un riego o de la
 * lluvia. Se recorre día por día desde la siembra hasta hoy sumando el agua (riegos + lluvia); cada vez que lo
 * acumulado llega a mmPorRiego, ese día cuenta como "agua completa" y se empieza a sumar de nuevo.
 *
 * Riego de asiento: al sembrar el cultivo ya necesita su primer riego completo. Mientras no lo reciba:
 *   días desde la siembra <= toleranciaDias             -> TOCA_REGAR (el riego vence el día de la siembra)
 *   si no                                               -> ATRASADO
 * Después, con el último día de agua completa:
 *   días sin agua < diasEntreRiegos                     -> AL_DIA
 *   días sin agua <= diasEntreRiegos + toleranciaDias   -> TOCA_REGAR
 *   si no                                               -> ATRASADO
 */
public final class ReglasRiego {

    /**
     * @param ultimaAgua        último día en que el cultivo recibió agua completa (o la siembra, si nunca la recibió)
     * @param origen            de dónde vino esa agua (SIEMBRA = todavía no recibió el riego de asiento)
     * @param mmDesdeUltimaAgua agua sumada después de ese día, que todavía no llega a un riego completo
     * @param diasSinAgua       días entre ultimaAgua y hoy
     * @param proximoRiego      cuándo vence el riego: ultimaAgua + diasEntreRiegos, o la siembra si nunca recibió agua completa
     * @param lluviaTotalMm     lluvia sumada desde la siembra (solo los días con dato)
     */
    public record Resultado(EstadoRiego estado, LocalDate ultimaAgua, OrigenAgua origen, BigDecimal mmDesdeUltimaAgua,
                            long diasSinAgua, LocalDate proximoRiego, BigDecimal lluviaTotalMm) {
    }

    private ReglasRiego() {
    }

    /**
     * @param siembra   siembra real de la campaña
     * @param riegosMm  mm regados por día (si hubo dos riegos el mismo día, ya sumados)
     * @param lluviaMm  mm de lluvia por día; puede estar vacío si no se pudo consultar
     */
    public static Resultado calcular(LocalDate siembra, Map<LocalDate, BigDecimal> riegosMm,
                                     Map<LocalDate, BigDecimal> lluviaMm, int diasEntreRiegos, BigDecimal mmPorRiego,
                                     int toleranciaDias, LocalDate hoy) {
        LocalDate ultimaAgua = siembra;
        OrigenAgua origen = OrigenAgua.SIEMBRA;
        BigDecimal acumulado = BigDecimal.ZERO;
        BigDecimal lluviaTotal = BigDecimal.ZERO;

        for (LocalDate dia = siembra; !dia.isAfter(hoy); dia = dia.plusDays(1)) {
            BigDecimal riego = riegosMm.getOrDefault(dia, BigDecimal.ZERO);
            BigDecimal lluvia = lluviaMm.getOrDefault(dia, BigDecimal.ZERO);
            lluviaTotal = lluviaTotal.add(lluvia);
            acumulado = acumulado.add(riego).add(lluvia);
            if (acumulado.compareTo(mmPorRiego) >= 0) {
                ultimaAgua = dia;
                // si ese día hubo un riego, el mérito es del riego (aunque también haya llovido)
                origen = riego.signum() > 0 ? OrigenAgua.RIEGO : OrigenAgua.LLUVIA;
                acumulado = BigDecimal.ZERO;
            }
        }

        long diasSinAgua = ChronoUnit.DAYS.between(ultimaAgua, hoy);
        // sin agua completa desde la siembra, el riego de asiento vence el mismo día de la siembra
        int plazo = origen == OrigenAgua.SIEMBRA ? 0 : diasEntreRiegos;
        EstadoRiego estado;
        if (diasSinAgua < plazo) {
            estado = EstadoRiego.AL_DIA;
        } else if (diasSinAgua <= (long) plazo + toleranciaDias) {
            estado = EstadoRiego.TOCA_REGAR;
        } else {
            estado = EstadoRiego.ATRASADO;
        }
        return new Resultado(estado, ultimaAgua, origen, acumulado, diasSinAgua,
                ultimaAgua.plusDays(plazo), lluviaTotal);
    }
}
