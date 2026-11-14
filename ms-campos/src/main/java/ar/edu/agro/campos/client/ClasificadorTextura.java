package ar.edu.agro.campos.client;

import ar.edu.agro.campos.config.ApisExternasProperties;
import ar.edu.agro.commons.contract.ClaseTextura;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

/**
 * Clase de textura a partir de los porcentajes, con la regla simplificada de la propuesta:
 * ARENOSO si la arena es el umbral o más (50 %), ARCILLOSO si la arcilla es el umbral o más (35 %),
 * FRANCO en otro caso. Los umbrales salen de la configuración. Se evalúa en ese orden.
 *
 * También sirve para clasificar los porcentajes que el AGRONOMO carga a mano (PATCH /lotes/{id}/suelo).
 */
@Component
public class ClasificadorTextura {

    private final BigDecimal umbralArena;
    private final BigDecimal umbralArcilla;

    public ClasificadorTextura(ApisExternasProperties props) {
        this.umbralArena = props.textura().umbralArenaPct();
        this.umbralArcilla = props.textura().umbralArcillaPct();
    }

    public ClaseTextura clasificar(BigDecimal arenaPct, BigDecimal arcillaPct) {
        if (arenaPct.compareTo(umbralArena) >= 0) {
            return ClaseTextura.ARENOSO;
        }
        if (arcillaPct.compareTo(umbralArcilla) >= 0) {
            return ClaseTextura.ARCILLOSO;
        }
        return ClaseTextura.FRANCO;
    }
}
