package ar.edu.agro.tratamientos.config;

import ar.edu.agro.commons.contract.ClaseTextura;
import ar.edu.agro.tratamientos.model.*;
import ar.edu.agro.tratamientos.repository.*;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Datos de prueba locales del catálogo. Solo cargan si no hay cultivos, así que reiniciar no duplica nada.
 * Los ids salen en orden, y los usa la colección de pruebas:
 *   cultivos: 1 Soja, 2 Maíz (los dos con riego), 3 Trigo (a secano) · plagas: 1 Oruga militar, 2 Chinche verde, 3 Roya
 *   agroquímicos: 1 Clorpirifos Plus (habilitado), 2 Piretroide Max (restringido en Córdoba),
 *                 3 Glifosato Pro (sin habilitaciones), 4 Fungicida Triazol
 * Son valores de ejemplo, no fichas técnicas reales.
 */
@Component
@ConditionalOnProperty(name = "agro.datos-iniciales", havingValue = "true", matchIfMissing = true)
public class DatosIniciales implements ApplicationRunner {

    private final CultivoRepository cultivos;
    private final PlagaRepository plagas;
    private final AgroquimicoRepository agroquimicos;
    private final HabilitacionRepository habilitaciones;
    private final AjusteSueloRepository ajustes;
    private final RestriccionTerritorialRepository restricciones;

    public DatosIniciales(CultivoRepository cultivos, PlagaRepository plagas, AgroquimicoRepository agroquimicos,
                          HabilitacionRepository habilitaciones, AjusteSueloRepository ajustes,
                          RestriccionTerritorialRepository restricciones) {
        this.cultivos = cultivos;
        this.plagas = plagas;
        this.agroquimicos = agroquimicos;
        this.habilitaciones = habilitaciones;
        this.ajustes = ajustes;
        this.restricciones = restricciones;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (cultivos.count() > 0) {
            completarRiego();
            return;
        }
        // riego: soja cada 7 días con 25 mm, maíz cada 5 días con 30 mm; el trigo se hace a secano
        Cultivo soja = cultivo("Soja", "Leguminosa", 150, 10, 12, 7, "25");
        Cultivo maiz = cultivo("Maíz", "Gramínea", 160, 9, 12, 5, "30");
        Cultivo trigo = cultivo("Trigo", "Gramínea", 180, 5, 7, null, null);

        Plaga oruga = plaga("Oruga militar");
        Plaga chinche = plaga("Chinche verde");
        Plaga roya = plaga("Roya");

        Agroquimico clorpirifos = agro("Clorpirifos Plus", "Clorpirifos", "0.500", "1.500", "l", "20", "150000", 21, 14);
        habilitar(clorpirifos, soja, oruga);
        habilitar(clorpirifos, soja, chinche);
        habilitar(clorpirifos, maiz, oruga);
        ajuste(clorpirifos, ClaseTextura.ARENOSO, "0.80");   // en suelo arenoso la dosis máxima baja a 1.2
        ajuste(clorpirifos, ClaseTextura.ARCILLOSO, "1.00");
        ajuste(clorpirifos, ClaseTextura.FRANCO, "1.00");

        Agroquimico piretroide = agro("Piretroide Max", "Cipermetrina", "0.100", "0.400", "l", "5", "40000", 14, 10);
        habilitar(piretroide, soja, chinche);
        ajuste(piretroide, ClaseTextura.ARENOSO, "0.70");
        restriccion(piretroide, "Córdoba", null);

        agro("Glifosato Pro", "Glifosato", "1.000", "3.000", "l", "20", "90000", 7, 7);   // sin habilitaciones: prueba T1

        Agroquimico fungicida = agro("Fungicida Triazol", "Tebuconazol", "0.300", "0.800", "l", "10", "70000", 30, 21);
        habilitar(fungicida, trigo, roya);
        habilitar(fungicida, soja, roya);
    }

    /**
     * Bases creadas antes de agregar el riego: la soja y el maíz de prueba quedaron sin frecuencia (como a secano).
     * Se completan una sola vez; si alguien ya les cargó un valor, no se toca.
     */
    private void completarRiego() {
        for (Cultivo c : cultivos.findAll()) {
            if (c.getDiasEntreRiegos() != null) {
                continue;
            }
            if ("Soja".equalsIgnoreCase(c.getNombre())) {
                c.setDiasEntreRiegos(7);
                c.setMmPorRiego(new BigDecimal("25"));
            } else if ("Maíz".equalsIgnoreCase(c.getNombre())) {
                c.setDiasEntreRiegos(5);
                c.setMmPorRiego(new BigDecimal("30"));
            }
        }
    }

    private Cultivo cultivo(String nombre, String familia, int dias, int desde, int hasta,
                            Integer diasEntreRiegos, String mmPorRiego) {
        Cultivo c = new Cultivo();
        c.setNombre(nombre);
        c.setFamilia(familia);
        c.setDiasCiclo(dias);
        c.setMesSiembraDesde(desde);
        c.setMesSiembraHasta(hasta);
        c.setDiasEntreRiegos(diasEntreRiegos);
        c.setMmPorRiego(mmPorRiego == null ? null : new BigDecimal(mmPorRiego));
        return cultivos.save(c);
    }

    private Plaga plaga(String nombre) {
        Plaga p = new Plaga();
        p.setNombre(nombre);
        return plagas.save(p);
    }

    private Agroquimico agro(String nombre, String principio, String dmin, String dmax, String unidad, String envase,
                             String precio, int carencia, int intervalo) {
        Agroquimico a = new Agroquimico();
        a.setNombre(nombre);
        a.setPrincipioActivo(principio);
        a.setDosisMinHa(new BigDecimal(dmin));
        a.setDosisMaxHa(new BigDecimal(dmax));
        a.setUnidad(unidad);
        a.setContenidoEnvase(new BigDecimal(envase));
        a.setPrecioEnvase(new BigDecimal(precio));
        a.setDiasCarencia(carencia);
        a.setIntervaloMinDias(intervalo);
        return agroquimicos.save(a);
    }

    private void habilitar(Agroquimico a, Cultivo c, Plaga p) {
        Habilitacion h = new Habilitacion();
        h.setAgroquimico(a);
        h.setCultivo(c);
        h.setPlaga(p);
        habilitaciones.save(h);
    }

    private void ajuste(Agroquimico a, ClaseTextura t, String factor) {
        AjusteSuelo x = new AjusteSuelo();
        x.setAgroquimico(a);
        x.setClaseTextura(t);
        x.setFactorDosisMax(new BigDecimal(factor));
        ajustes.save(x);
    }

    private void restriccion(Agroquimico a, String provincia, String departamento) {
        RestriccionTerritorial r = new RestriccionTerritorial();
        r.setAgroquimico(a);
        r.setProvincia(provincia);
        r.setDepartamento(departamento);
        restricciones.save(r);
    }
}
