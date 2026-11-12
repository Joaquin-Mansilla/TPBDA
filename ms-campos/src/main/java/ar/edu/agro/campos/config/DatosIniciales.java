package ar.edu.agro.campos.config;

import ar.edu.agro.commons.contract.ClaseTextura;
import ar.edu.agro.campos.model.*;
import ar.edu.agro.campos.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

/**
 * Datos de prueba locales. Solo cargan si no hay establecimientos, así que reiniciar no duplica nada.
 * Los sub de los usuarios son configurables (agro.seed.*) y tienen que coincidir con el realm de Keycloak.
 * Ids resultantes: establecimientos 1 (productor1, con operario1) y 2 (productor2);
 *   lotes: 1 Norte (suelo OK franco, campaña 1 SEMBRADA con soja y una aplicación reciente de Clorpirifos),
 *          2 Sur (SIN_DATOS, campaña 2 SEMBRADA), 3 Este (suelo OK arenoso, dos campañas de soja ya cosechadas: prueba C3),
 *          4 del establecimiento de productor2.
 * Los ids de cultivo, plaga y agroquímico son los del catálogo de ms-tratamientos (Soja 1, Maíz 2; Oruga 1, Chinche 2;
 * Clorpirifos 1). Las fechas están pensadas para noviembre de 2026.
 */
@Component
@ConditionalOnProperty(name = "agro.datos-iniciales", havingValue = "true", matchIfMissing = true)
public class DatosIniciales implements ApplicationRunner {

    private final EstablecimientoRepository establecimientos;
    private final LoteRepository lotes;
    private final CampaniaRepository campanias;
    private final AplicacionRepository aplicaciones;
    private final String productor1;
    private final String productor2;
    private final String operario1;

    public DatosIniciales(EstablecimientoRepository establecimientos, LoteRepository lotes, CampaniaRepository campanias,
                          AplicacionRepository aplicaciones,
                          @Value("${agro.seed.productor1-sub}") String productor1,
                          @Value("${agro.seed.productor2-sub}") String productor2,
                          @Value("${agro.seed.operario1-sub}") String operario1) {
        this.establecimientos = establecimientos;
        this.lotes = lotes;
        this.campanias = campanias;
        this.aplicaciones = aplicaciones;
        this.productor1 = productor1;
        this.productor2 = productor2;
        this.operario1 = operario1;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (establecimientos.count() > 0) {
            return;
        }
        Establecimiento e1 = establecimiento("La Esperanza", productor1, Set.of(operario1));
        Establecimiento e2 = establecimiento("Los Aromos", productor2, Set.of());

        Lote norte = lote(e1, "Lote Norte", "-32.700000", "-62.100000", "100.00", true, ClaseTextura.FRANCO, "25", "40", "35");
        Lote sur = lote(e1, "Lote Sur", "-32.710000", "-62.120000", "50.00", false, null, null, null, null);
        Lote este = lote(e1, "Lote Este", "-32.720000", "-62.140000", "80.00", true, ClaseTextura.ARENOSO, "10", "75", "15");
        lote(e2, "Lote Chico", "-32.800000", "-62.300000", "20.00", true, ClaseTextura.FRANCO, "25", "40", "35");

        Campania c1 = campania(norte, 1L, "80.00", "2026-11-01", "2027-03-31", EstadoCampania.SEMBRADA);
        campania(sur, 1L, "40.00", "2026-11-01", "2027-03-31", EstadoCampania.SEMBRADA);
        campania(este, 1L, "70.00", "2024-11-10", "2025-04-09", EstadoCampania.COSECHADA);
        campania(este, 1L, "70.00", "2025-11-10", "2026-04-09", EstadoCampania.COSECHADA);

        // aplicación reciente de Clorpirifos (intervalo 14 días): prueba T3
        Aplicacion a = new Aplicacion();
        a.setCampania(c1);
        a.setAgroquimicoId(1L);
        a.setPlagaId(1L);
        a.setDosisPorHa(new BigDecimal("1.000"));
        a.setSuperficieTratadaHa(new BigDecimal("80.00"));
        a.setFecha(LocalDate.parse("2026-11-05"));
        a.setEstado(EstadoAplicacion.REGISTRADA);
        a.setRegistradaPorSub(operario1);
        aplicaciones.save(a);
    }

    private Establecimiento establecimiento(String nombre, String productorSub, Set<String> operarios) {
        Establecimiento e = new Establecimiento();
        e.setNombre(nombre);
        e.setProductorSub(productorSub);
        e.setOperarios(new HashSet<>(operarios));
        return establecimientos.save(e);
    }

    private Lote lote(Establecimiento e, String nombre, String lat, String lon, String sup, boolean conSuelo,
                      ClaseTextura clase, String arcilla, String arena, String limo) {
        Lote l = new Lote();
        l.setEstablecimiento(e);
        l.setNombre(nombre);
        l.setLatitud(new BigDecimal(lat));
        l.setLongitud(new BigDecimal(lon));
        l.setSuperficieHa(new BigDecimal(sup));
        if (conSuelo) {
            l.setProvincia("Córdoba");
            l.setDepartamento("Marcos Juárez");
            l.setEstadoSuelo(EstadoSuelo.OK);
            l.setClaseTextura(clase);
            l.setArcillaPct(new BigDecimal(arcilla));
            l.setArenaPct(new BigDecimal(arena));
            l.setLimoPct(new BigDecimal(limo));
            l.setPh(new BigDecimal("6.50"));
            l.setCarbonoOrganico(new BigDecimal("15.00"));
        } else {
            l.setEstadoSuelo(EstadoSuelo.SIN_DATOS);
        }
        return lotes.save(l);
    }

    private Campania campania(Lote lote, Long cultivoId, String sup, String siembra, String cosecha, EstadoCampania estado) {
        Campania c = new Campania();
        c.setLote(lote);
        c.setCultivoId(cultivoId);
        c.setSuperficieSembradaHa(new BigDecimal(sup));
        c.setFechaSiembra(LocalDate.parse(siembra));
        c.setCosechaEstimada(LocalDate.parse(cosecha));
        c.setEstado(estado);
        return campanias.save(c);
    }
}
