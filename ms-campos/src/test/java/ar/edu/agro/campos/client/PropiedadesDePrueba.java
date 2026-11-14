package ar.edu.agro.campos.client;

import ar.edu.agro.campos.config.ApisExternasProperties;
import java.math.BigDecimal;
import java.time.Duration;

/** Configuración fija para los tests de los clientes. */
final class PropiedadesDePrueba {

    private PropiedadesDePrueba() {
    }

    static ApisExternasProperties con(int reintentos) {
        return new ApisExternasProperties(true,
                new ApisExternasProperties.SoilGrids("http://soilgrids.test/v2.0", "0-5cm",
                        Duration.ofSeconds(3), Duration.ofSeconds(8), reintentos),
                new ApisExternasProperties.Georef("http://georef.test/v2.0",
                        Duration.ofSeconds(3), Duration.ofSeconds(5), reintentos),
                new ApisExternasProperties.Textura(new BigDecimal("50"), new BigDecimal("35")));
    }
}
