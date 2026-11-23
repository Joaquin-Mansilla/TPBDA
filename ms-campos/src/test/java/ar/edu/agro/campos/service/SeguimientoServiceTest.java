package ar.edu.agro.campos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ar.edu.agro.campos.client.ApiExternaException;
import ar.edu.agro.campos.client.TratamientosClient;
import ar.edu.agro.campos.model.Aplicacion;
import ar.edu.agro.campos.model.Campania;
import ar.edu.agro.campos.model.EstadoAplicacion;
import ar.edu.agro.campos.model.EstadoCampania;
import ar.edu.agro.campos.model.Establecimiento;
import ar.edu.agro.campos.model.Lote;
import ar.edu.agro.campos.model.Monitoreo;
import ar.edu.agro.campos.repository.AplicacionRepository;
import ar.edu.agro.campos.repository.CampaniaRepository;
import ar.edu.agro.campos.repository.MonitoreoRepository;
import ar.edu.agro.campos.repository.RiegoRepository;
import ar.edu.agro.commons.contract.CultivoResumenResponse;
import ar.edu.agro.commons.security.Roles;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Alertas de monitoreo (M1, umbral 20 %) e historial del lote con el rendimiento por cultivo. */
class SeguimientoServiceTest {

    private final Usuario productor = new Usuario("prod-1", Set.of(Roles.PRODUCTOR));

    private CampaniaRepository campanias;
    private MonitoreoRepository monitoreos;
    private AplicacionRepository aplicaciones;
    private TratamientosClient tratamientos;
    private SeguimientoService servicio;
    private Lote lote;
    private Campania soja;
    private final List<Monitoreo> delMonitoreo = new ArrayList<>();
    private final List<Aplicacion> delaCampania = new ArrayList<>();

    @BeforeEach
    void preparar() {
        campanias = mock(CampaniaRepository.class);
        monitoreos = mock(MonitoreoRepository.class);
        aplicaciones = mock(AplicacionRepository.class);
        RiegoRepository riegos = mock(RiegoRepository.class);
        tratamientos = mock(TratamientosClient.class);
        CampaniaService campaniaService = mock(CampaniaService.class);
        LoteService lotes = mock(LoteService.class);

        Establecimiento e = new Establecimiento();
        e.setId(1L);
        e.setProductorSub("prod-1");
        lote = new Lote();
        lote.setId(2L);
        lote.setNombre("Lote Demo");
        lote.setEstablecimiento(e);
        lote.setSuperficieHa(new BigDecimal("100"));
        soja = campania(5L, 1L, "60");

        when(lotes.buscar(2L)).thenReturn(lote);
        when(campaniaService.buscar(5L)).thenReturn(soja);
        when(campaniaService.umbralAlertaPct()).thenReturn(new BigDecimal("20"));
        when(campaniaService.esAlerta(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(i -> i.<BigDecimal>getArgument(0).compareTo(new BigDecimal("20")) >= 0);
        when(monitoreos.findByCampaniaIdOrderByFechaAscIdAsc(anyLong())).thenReturn(List.of());
        when(monitoreos.findByCampaniaIdOrderByFechaAscIdAsc(5L)).thenReturn(delMonitoreo);
        when(aplicaciones.findByCampaniaIdOrderByFechaAsc(anyLong())).thenReturn(List.of());
        when(aplicaciones.findByCampaniaIdOrderByFechaAsc(5L)).thenReturn(delaCampania);
        when(riegos.findByCampaniaIdOrderByFechaAsc(anyLong())).thenReturn(List.of());
        when(tratamientos.obtenerCultivo(1L)).thenReturn(new CultivoResumenResponse(1L, "Soja", "Leguminosa", 150, 10, 12));
        when(tratamientos.obtenerCultivo(2L)).thenReturn(new CultivoResumenResponse(2L, "Maíz", "Gramínea", 160, 9, 12));

        servicio = new SeguimientoService(campanias, monitoreos, aplicaciones, riegos, campaniaService, lotes,
                new AccesoService(), tratamientos);
    }

    private Campania campania(Long id, Long cultivoId, String ha) {
        Campania c = new Campania();
        c.setId(id);
        c.setLote(lote);
        c.setCultivoId(cultivoId);
        c.setSuperficieSembradaHa(new BigDecimal(ha));
        c.setFechaSiembra(LocalDate.of(2026, 11, 1));
        c.setCosechaEstimada(LocalDate.of(2027, 3, 31));
        c.sembrar(LocalDate.of(2026, 11, 1), LocalDate.of(2027, 3, 31));
        return c;
    }

    private void monitoreo(Long id, Long plagaId, String incidencia, String fecha) {
        Monitoreo m = new Monitoreo();
        m.setId(id);
        m.setCampania(soja);
        m.setPlagaId(plagaId);
        m.setIncidenciaPct(new BigDecimal(incidencia));
        m.setFecha(LocalDate.parse(fecha));
        delMonitoreo.add(m);
    }

    private void aplicacion(Long id, Long plagaId, String fecha, EstadoAplicacion estado) {
        Aplicacion a = new Aplicacion();
        a.setId(id);
        a.setCampania(soja);
        a.setPlagaId(plagaId);
        a.setFecha(LocalDate.parse(fecha));
        a.setEstado(estado);
        delaCampania.add(a);
    }

    @Test
    void unMonitoreoSobreElUmbralSinAplicacionQuedaPendiente() {
        monitoreo(10L, 1L, "35", "2026-11-15");
        monitoreo(11L, 2L, "5", "2026-11-15");   // debajo del umbral: no es alerta

        var alertas = servicio.alertas(5L, false, productor);

        assertThat(alertas).singleElement().satisfies(a -> {
            assertThat(a.monitoreoId()).isEqualTo(10L);
            assertThat(a.atendida()).isFalse();
            assertThat(a.mensaje()).startsWith("Pendiente");
        });
    }

    @Test
    void unaAplicacionHechaContraLaPlagaDesdeElMonitoreoLaAtiende() {
        monitoreo(10L, 1L, "35", "2026-11-15");
        aplicacion(90L, 1L, "2026-11-16", EstadoAplicacion.REGISTRADA);

        var alertas = servicio.alertas(5L, false, productor);

        assertThat(alertas).singleElement().satisfies(a -> {
            assertThat(a.atendida()).isTrue();
            assertThat(a.aplicacionId()).isEqualTo(90L);
        });
        assertThat(servicio.alertas(5L, true, productor)).isEmpty();
    }

    @Test
    void noLaAtiendenUnaAplicacionAnteriorDeOtraPlagaNiUnaPendiente() {
        monitoreo(10L, 1L, "35", "2026-11-15");
        aplicacion(90L, 1L, "2026-11-14", EstadoAplicacion.REGISTRADA);                 // anterior al monitoreo
        aplicacion(91L, 2L, "2026-11-16", EstadoAplicacion.REGISTRADA);                 // otra plaga
        aplicacion(92L, 1L, "2026-11-16", EstadoAplicacion.PENDIENTE_DE_VALIDACION);    // todavía no se hizo

        assertThat(servicio.alertas(5L, true, productor)).hasSize(1);
    }

    @Test
    void elHistorialTraeLasCampaniasConNombreYRendimientoPorCultivo() {
        soja.cosechar(LocalDate.of(2027, 3, 30), new BigDecimal("210000"));
        Campania maiz = campania(6L, 2L, "40");
        when(campanias.findByLoteIdOrderByFechaSiembraDesc(2L)).thenReturn(List.of(soja, maiz));
        monitoreo(10L, 1L, "35", "2026-11-15");

        var h = servicio.historial(2L, productor);

        assertThat(h.campanias()).hasSize(2);
        assertThat(h.campanias().get(0).cultivo()).isEqualTo("Soja");
        assertThat(h.campanias().get(0).rendimientoKgHa()).isEqualByComparingTo("3500.0");
        assertThat(h.campanias().get(0).alertasPendientes()).isEqualTo(1);
        assertThat(h.campanias().get(1).estado()).isEqualTo(EstadoCampania.SEMBRADA);
        // el maíz todavía no se cosechó: solo la soja tiene rendimiento
        assertThat(h.rendimientos()).singleElement().satisfies(r -> {
            assertThat(r.cultivo()).isEqualTo("Soja");
            assertThat(r.rendimientoPromedioKgHa()).isEqualByComparingTo("3500.0");
        });
    }

    @Test
    void siMsTratamientosNoRespondeElHistorialSaleSinNombres() {
        when(campanias.findByLoteIdOrderByFechaSiembraDesc(2L)).thenReturn(List.of(soja));
        when(tratamientos.obtenerCultivo(1L)).thenThrow(new ApiExternaException("Tratamientos", "no respondió"));

        var h = servicio.historial(2L, productor);

        assertThat(h.campanias()).singleElement().satisfies(c -> assertThat(c.cultivo()).isNull());
    }

    @Test
    void elRendimientoPromedioEsPonderadoPorHectarea() {
        Campania a = campania(7L, 1L, "10");
        a.cosechar(LocalDate.of(2027, 3, 30), new BigDecimal("20000"));   // 2.000 kg/ha
        Campania b = campania(8L, 1L, "30");
        b.cosechar(LocalDate.of(2027, 3, 30), new BigDecimal("120000"));  // 4.000 kg/ha

        var r = SeguimientoService.rendimientos(List.of(a, b), Map.of(1L, "Soja"));

        // (20.000 + 120.000) / 40 ha = 3.500, no el promedio simple (3.000)
        assertThat(r).singleElement().satisfies(x -> assertThat(x.rendimientoPromedioKgHa()).isEqualByComparingTo("3500.0"));
    }
}
