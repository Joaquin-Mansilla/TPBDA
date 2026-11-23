package ar.edu.agro.campos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.agro.campos.client.ApiExternaException;
import ar.edu.agro.campos.client.TratamientosClient;
import ar.edu.agro.campos.dto.CamposDtos.CampaniaRequest;
import ar.edu.agro.campos.dto.CamposDtos.CampaniaResponse;
import ar.edu.agro.campos.dto.CamposDtos.CosechaRequest;
import ar.edu.agro.campos.dto.CamposDtos.MonitoreoRequest;
import ar.edu.agro.campos.dto.CamposDtos.PerdidaRequest;
import ar.edu.agro.campos.dto.CamposDtos.SiembraRequest;
import ar.edu.agro.campos.model.Campania;
import ar.edu.agro.campos.model.EstadoCampania;
import ar.edu.agro.campos.model.Establecimiento;
import ar.edu.agro.campos.model.Lote;
import ar.edu.agro.campos.model.Monitoreo;
import ar.edu.agro.campos.repository.CampaniaRepository;
import ar.edu.agro.campos.repository.MonitoreoRepository;
import ar.edu.agro.commons.contract.CultivoResumenResponse;
import ar.edu.agro.commons.error.ReglaNegocioException;
import ar.edu.agro.commons.security.Roles;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

/** Campañas: planificación (C1 a C4) sin retener la transacción mientras se consulta a ms-tratamientos. */
class CampaniaServiceTest {

    private static final CultivoResumenResponse SOJA = new CultivoResumenResponse(1L, "Soja", "Leguminosa", 150, 10, 12);
    /** "hoy" fijo para las reglas que comparan con la fecha actual (C8) */
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2027-06-01T15:00:00Z"), ZoneId.of("America/Argentina/Buenos_Aires"));

    private final Usuario productor = new Usuario("prod-1", Set.of(Roles.PRODUCTOR));

    private CampaniaRepository campanias;
    private MonitoreoRepository monitoreos;
    private TratamientosClient tratamientos;
    private TransactionTemplate tx;
    private CampaniaService servicio;
    private Lote lote;
    private final List<Campania> delLote = new ArrayList<>();

    @BeforeEach
    void preparar() {
        campanias = mock(CampaniaRepository.class);
        tratamientos = mock(TratamientosClient.class);
        tx = mock(TransactionTemplate.class);
        when(tx.execute(any())).thenAnswer(i -> i.<TransactionCallback<?>>getArgument(0).doInTransaction(null));
        LoteService lotes = mock(LoteService.class);

        Establecimiento e = new Establecimiento();
        e.setId(1L);
        e.setProductorSub("prod-1");
        lote = new Lote();
        lote.setId(3L);
        lote.setEstablecimiento(e);
        lote.setSuperficieHa(new BigDecimal("80"));
        when(lotes.buscar(3L)).thenReturn(lote);

        when(campanias.findByLoteIdOrderByFechaSiembraDesc(3L)).thenReturn(delLote);
        when(campanias.save(any(Campania.class))).thenAnswer(i -> {
            Campania c = i.getArgument(0);
            if (c.getId() == null) {
                c.setId(20L);
            }
            return c;
        });
        when(tratamientos.obtenerCultivo(1L)).thenReturn(SOJA);

        monitoreos = mock(MonitoreoRepository.class);
        when(monitoreos.save(any(Monitoreo.class))).thenAnswer(i -> i.getArgument(0));
        servicio = new CampaniaService(campanias, monitoreos, lotes, new AccesoService(), tratamientos, tx, RELOJ, 45, new BigDecimal("20"), 10);
    }

    private Campania existente(Long id, String siembra, String cosecha, EstadoCampania estado) {
        Campania c = new Campania();
        c.setId(id);
        c.setLote(lote);
        c.setCultivoId(1L);
        c.setSuperficieSembradaHa(new BigDecimal("70"));
        c.setFechaSiembra(LocalDate.parse(siembra));
        c.setCosechaEstimada(LocalDate.parse(cosecha));
        if (estado != EstadoCampania.PLANIFICADA) {
            c.sembrar(c.getFechaSiembra(), c.getCosechaEstimada());
        }
        if (estado == EstadoCampania.COSECHADA) {
            c.cosechar(c.getCosechaEstimada());
        }
        return c;
    }

    @Test
    void planificarGuardaUnaCampaniaPlanificadaConLaCosechaCalculada() {
        CampaniaResponse r = servicio.planificar(3L, new CampaniaRequest(1L, new BigDecimal("50"), LocalDate.of(2026, 11, 15)), productor);

        assertThat(r.estado()).isEqualTo(EstadoCampania.PLANIFICADA);
        assertThat(r.cosechaEstimada()).isEqualTo(LocalDate.of(2027, 4, 14));
    }

    @Test
    void planificarConsultaAMsTratamientosAntesDeAbrirLaTransaccion() {
        servicio.planificar(3L, new CampaniaRequest(1L, new BigDecimal("50"), LocalDate.of(2026, 11, 15)), productor);

        InOrder orden = inOrder(tratamientos, tx, campanias);
        orden.verify(tratamientos).obtenerCultivo(1L);
        orden.verify(tx).execute(any());
        orden.verify(campanias).save(any(Campania.class));
    }

    @Test
    void siMsTratamientosNoRespondeNoSeAbreNingunaTransaccion() {
        when(tratamientos.obtenerCultivo(1L)).thenThrow(new ApiExternaException("Tratamientos", "no respondió"));

        assertThatThrownBy(() -> servicio.planificar(3L, new CampaniaRequest(1L, new BigDecimal("50"), LocalDate.of(2026, 11, 15)), productor))
                .isInstanceOf(ApiExternaException.class);
        verify(tx, never()).execute(any());
        verify(campanias, never()).save(any());
    }

    @Test
    void laRotacionC3UsaLasFamiliasPedidasAntesDeLaTransaccion() {
        delLote.add(existente(11L, "2025-11-10", "2026-04-09", EstadoCampania.COSECHADA));
        delLote.add(existente(10L, "2024-11-10", "2025-04-09", EstadoCampania.COSECHADA));

        assertThatThrownBy(() -> servicio.planificar(3L, new CampaniaRequest(1L, new BigDecimal("50"), LocalDate.of(2026, 11, 15)), productor))
                .isInstanceOf(ReglaNegocioException.class)
                .satisfies(ex -> assertThat(((ReglaNegocioException) ex).getMotivos()).anyMatch(m -> m.startsWith("C3")));
        verify(campanias, never()).save(any());
    }

    private Campania guardada(Campania c) {
        when(campanias.findById(c.getId())).thenReturn(Optional.of(c));
        return c;
    }

    private static void esC5(Throwable ex) {
        assertThat(ex).isInstanceOf(ReglaNegocioException.class);
        assertThat(((ReglaNegocioException) ex).getMotivos()).anyMatch(m -> m.startsWith("C5"));
    }

    @Test
    void registrarLaSiembraPasaASembradaYRecalculaLaCosecha() {
        guardada(existente(30L, "2026-11-15", "2027-04-14", EstadoCampania.PLANIFICADA));

        CampaniaResponse r = servicio.registrarSiembra(30L, new SiembraRequest(LocalDate.of(2026, 11, 20)), productor);

        assertThat(r.estado()).isEqualTo(EstadoCampania.SEMBRADA);
        assertThat(r.fechaSiembra()).isEqualTo(LocalDate.of(2026, 11, 15));
        assertThat(r.fechaSiembraReal()).isEqualTo(LocalDate.of(2026, 11, 20));
        assertThat(r.cosechaEstimada()).isEqualTo(LocalDate.of(2027, 4, 19));
    }

    @Test
    void laSiembraPideElCultivoAntesDeAbrirLaTransaccion() {
        guardada(existente(30L, "2026-11-15", "2027-04-14", EstadoCampania.PLANIFICADA));

        servicio.registrarSiembra(30L, new SiembraRequest(LocalDate.of(2026, 11, 20)), productor);

        InOrder orden = inOrder(tratamientos, tx, campanias);
        orden.verify(tratamientos).obtenerCultivo(1L);
        orden.verify(tx).execute(any());
        orden.verify(campanias).save(any(Campania.class));
    }

    @Test
    void sembrarOtraVezEsC5SinConsultarAMsTratamientos() {
        guardada(existente(30L, "2026-11-15", "2027-04-14", EstadoCampania.SEMBRADA));

        assertThatThrownBy(() -> servicio.registrarSiembra(30L, new SiembraRequest(LocalDate.of(2026, 11, 20)), productor))
                .satisfies(CampaniaServiceTest::esC5);
        verify(tratamientos, never()).obtenerCultivo(any());
        verify(tx, never()).execute(any());
    }

    @Test
    void cosecharUnaCampaniaSembrada() {
        guardada(existente(30L, "2026-11-01", "2027-03-31", EstadoCampania.SEMBRADA));

        CampaniaResponse r = servicio.registrarCosecha(30L, new CosechaRequest(LocalDate.of(2027, 3, 25)), productor);

        assertThat(r.estado()).isEqualTo(EstadoCampania.COSECHADA);
        assertThat(r.fechaCosecha()).isEqualTo(LocalDate.of(2027, 3, 25));
    }

    // ---- C9: se cosecha desde 10 días antes de la estimada (31/03/2027); hoy es 01/06/2027
    @Test
    void c9SePuedeCosecharJusto10DiasAntesDeLaEstimada() {
        guardada(existente(30L, "2026-11-01", "2027-03-31", EstadoCampania.SEMBRADA));

        CampaniaResponse r = servicio.registrarCosecha(30L, new CosechaRequest(LocalDate.of(2027, 3, 21)), productor);

        assertThat(r.estado()).isEqualTo(EstadoCampania.COSECHADA);
    }

    @Test
    void c9Cosechar11DiasAntesDeLaEstimadaEsDemasiadoTemprano() {
        guardada(existente(30L, "2026-11-01", "2027-03-31", EstadoCampania.SEMBRADA));

        assertThatThrownBy(() -> servicio.registrarCosecha(30L, new CosechaRequest(LocalDate.of(2027, 3, 20)), productor))
                .satisfies(ex -> assertThat(motivos(ex)).containsExactly(
                        "C9: La cosecha (2027-03-20) es demasiado temprana: la estimada es el 2027-03-31 y se puede "
                        + "cosechar desde 10 días antes (2027-03-21)"));
        verify(campanias, never()).save(any());
    }

    @Test
    void c9SePuedeCosecharElMismoDiaDeLaEstimadaYDespues() {
        guardada(existente(30L, "2026-11-01", "2027-03-31", EstadoCampania.SEMBRADA));
        guardada(existente(31L, "2026-11-01", "2027-03-31", EstadoCampania.SEMBRADA));

        assertThat(servicio.registrarCosecha(30L, new CosechaRequest(LocalDate.of(2027, 3, 31)), productor).estado())
                .isEqualTo(EstadoCampania.COSECHADA);
        assertThat(servicio.registrarCosecha(31L, new CosechaRequest(LocalDate.of(2027, 4, 20)), productor).estado())
                .isEqualTo(EstadoCampania.COSECHADA);
    }

    @Test
    void cosecharSinHaberSembradoEsC5() {
        guardada(existente(30L, "2026-11-15", "2027-04-14", EstadoCampania.PLANIFICADA));

        assertThatThrownBy(() -> servicio.registrarCosecha(30L, new CosechaRequest(LocalDate.of(2027, 3, 20)), productor))
                .satisfies(CampaniaServiceTest::esC5);
    }

    @Test
    void perderGuardaFechaYMotivo() {
        guardada(existente(30L, "2026-11-01", "2027-03-31", EstadoCampania.SEMBRADA));

        CampaniaResponse r = servicio.registrarPerdida(30L, new PerdidaRequest(LocalDate.of(2026, 12, 1), "  Granizo  "), productor);

        assertThat(r.estado()).isEqualTo(EstadoCampania.PERDIDA);
        assertThat(r.fechaPerdida()).isEqualTo(LocalDate.of(2026, 12, 1));
        assertThat(r.motivoPerdida()).isEqualTo("Granizo");
    }

    @Test
    void perderSinMotivoEsC5() {
        Campania c = existente(30L, "2026-11-01", "2027-03-31", EstadoCampania.SEMBRADA);

        assertThatThrownBy(() -> c.perder(LocalDate.of(2026, 12, 1), " ")).satisfies(CampaniaServiceTest::esC5);
        assertThat(c.getEstado()).isEqualTo(EstadoCampania.SEMBRADA);
    }

    @Test
    void unaCampaniaCosechadaNoSePuedePerder() {
        guardada(existente(30L, "2024-11-10", "2025-04-09", EstadoCampania.COSECHADA));

        assertThatThrownBy(() -> servicio.registrarPerdida(30L, new PerdidaRequest(LocalDate.of(2025, 5, 1), "Granizo"), productor))
                .satisfies(CampaniaServiceTest::esC5);
    }

    // ------------------------------------------------------------------ C4 y C8: fechas reales (hoy = 2027-06-01)

    private static List<String> motivos(Throwable ex) {
        assertThat(ex).isInstanceOf(ReglaNegocioException.class);
        return ((ReglaNegocioException) ex).getMotivos();
    }

    @Test
    void laSiembraRealPuedeAtrasarseExactamenteLaTolerancia() {
        guardada(existente(30L, "2026-10-15", "2027-03-14", EstadoCampania.PLANIFICADA));

        CampaniaResponse r = servicio.registrarSiembra(30L, new SiembraRequest(LocalDate.of(2026, 11, 29)), productor);

        assertThat(r.fechaSiembraReal()).isEqualTo(LocalDate.of(2026, 11, 29));
    }

    @Test
    void laSiembraRealQueSeAtrasaUnDiaMasQueLaToleranciaEsC4() {
        guardada(existente(30L, "2026-10-15", "2027-03-14", EstadoCampania.PLANIFICADA));

        assertThatThrownBy(() -> servicio.registrarSiembra(30L, new SiembraRequest(LocalDate.of(2026, 11, 30)), productor))
                .satisfies(ex -> assertThat(motivos(ex)).anyMatch(m -> m.startsWith("C4") && m.contains("45 días")));
        verify(campanias, never()).save(any());
    }

    @Test
    void laSiembraRealAdelantadaMasQueLaToleranciaEsC4() {
        guardada(existente(30L, "2026-12-20", "2027-05-19", EstadoCampania.PLANIFICADA));

        assertThatThrownBy(() -> servicio.registrarSiembra(30L, new SiembraRequest(LocalDate.of(2026, 10, 1)), productor))
                .satisfies(ex -> assertThat(motivos(ex)).anyMatch(m -> m.startsWith("C4") && m.contains("45 días")));
    }

    @Test
    void laSiembraRealFueraDeLaVentanaDelCultivoEsC4() {
        guardada(existente(30L, "2026-12-20", "2027-05-19", EstadoCampania.PLANIFICADA));

        assertThatThrownBy(() -> servicio.registrarSiembra(30L, new SiembraRequest(LocalDate.of(2027, 1, 10)), productor))
                .satisfies(ex -> assertThat(motivos(ex)).anyMatch(m -> m.startsWith("C4") && m.contains("ventana")));
    }

    @Test
    void unaSiembraConFechaFuturaEsC8() {
        guardada(existente(30L, "2027-10-15", "2028-03-13", EstadoCampania.PLANIFICADA));

        assertThatThrownBy(() -> servicio.registrarSiembra(30L, new SiembraRequest(LocalDate.of(2027, 10, 20)), productor))
                .satisfies(ex -> assertThat(motivos(ex)).containsExactly("C8: No se puede registrar una siembra con fecha futura"));
    }

    @Test
    void laNuevaCosechaEstimadaNoPuedePisarseConOtraCampaniaC1() {
        Campania esta = guardada(existente(30L, "2026-11-01", "2027-03-31", EstadoCampania.PLANIFICADA));
        delLote.add(existente(31L, "2027-04-10", "2027-09-07", EstadoCampania.PLANIFICADA));
        delLote.add(esta);

        // con la siembra real del 20/11 la cosecha estimada pasa al 19/04/2027: se pisa con la campaña siguiente
        assertThatThrownBy(() -> servicio.registrarSiembra(30L, new SiembraRequest(LocalDate.of(2026, 11, 20)), productor))
                .satisfies(ex -> assertThat(motivos(ex)).anyMatch(m -> m.startsWith("C1")));
    }

    @Test
    void c9NoSePuedeCosecharElMismoDiaDeLaSiembra() {
        guardada(existente(30L, "2027-06-01", "2027-10-29", EstadoCampania.SEMBRADA));

        assertThatThrownBy(() -> servicio.registrarCosecha(30L, new CosechaRequest(LocalDate.of(2027, 6, 1)), productor))
                .satisfies(ex -> assertThat(motivos(ex)).singleElement().asString().startsWith("C9"));
    }

    @Test
    void unaCosechaAnteriorALaSiembraRealEsC8() {
        guardada(existente(30L, "2026-11-01", "2027-03-31", EstadoCampania.SEMBRADA));

        assertThatThrownBy(() -> servicio.registrarCosecha(30L, new CosechaRequest(LocalDate.of(2026, 10, 31)), productor))
                .satisfies(ex -> assertThat(motivos(ex)).anyMatch(m -> m.startsWith("C8") && m.contains("anterior a la siembra")));
    }

    @Test
    void unaCosechaConFechaFuturaEsC8() {
        guardada(existente(30L, "2026-11-01", "2027-03-31", EstadoCampania.SEMBRADA));

        assertThatThrownBy(() -> servicio.registrarCosecha(30L, new CosechaRequest(LocalDate.of(2027, 6, 2)), productor))
                .satisfies(ex -> assertThat(motivos(ex)).containsExactly("C8: No se puede registrar una cosecha con fecha futura"));
    }

    @Test
    void unaPerdidaConFechaFuturaEsC8() {
        guardada(existente(30L, "2026-11-01", "2027-03-31", EstadoCampania.SEMBRADA));

        assertThatThrownBy(() -> servicio.registrarPerdida(30L, new PerdidaRequest(LocalDate.of(2027, 7, 1), "Granizo"), productor))
                .satisfies(ex -> assertThat(motivos(ex)).anyMatch(m -> m.startsWith("C8") && m.contains("futura")));
    }

    @Test
    void laPerdidaDeUnaSembradaNoPuedeSerAnteriorALaSiembraReal() {
        guardada(existente(30L, "2026-11-01", "2027-03-31", EstadoCampania.SEMBRADA));

        assertThatThrownBy(() -> servicio.registrarPerdida(30L, new PerdidaRequest(LocalDate.of(2026, 10, 1), "Granizo"), productor))
                .satisfies(ex -> assertThat(motivos(ex)).anyMatch(m -> m.startsWith("C8") && m.contains("anterior a la siembra")));
    }

    @Test
    void unaPlanificadaSePuedePerderAntesDeSuFechaDeSiembra() {
        guardada(existente(30L, "2027-03-01", "2027-07-29", EstadoCampania.PLANIFICADA));

        CampaniaResponse r = servicio.registrarPerdida(30L, new PerdidaRequest(LocalDate.of(2027, 2, 1), "Inundación"), productor);

        assertThat(r.estado()).isEqualTo(EstadoCampania.PERDIDA);
    }

    @Test
    void elMonitoreoSinFechaEsDeHoy() {
        guardada(existente(30L, "2027-03-01", "2027-07-29", EstadoCampania.SEMBRADA));

        var r = servicio.registrarMonitoreo(30L, new MonitoreoRequest(1L, new BigDecimal("20"), null), productor);

        assertThat(r.fecha()).isEqualTo(LocalDate.of(2027, 6, 1));
    }

    @Test
    void elMonitoreoElDiaDeLaSiembraEsValido() {
        guardada(existente(30L, "2027-03-01", "2027-07-29", EstadoCampania.SEMBRADA));

        var r = servicio.registrarMonitoreo(30L, new MonitoreoRequest(1L, new BigDecimal("20"), LocalDate.of(2027, 3, 1)), productor);

        assertThat(r.fecha()).isEqualTo(LocalDate.of(2027, 3, 1));
    }

    @Test
    void unMonitoreoAnteriorALaSiembraOFuturoEsC8() {
        guardada(existente(30L, "2027-03-01", "2027-07-29", EstadoCampania.SEMBRADA));

        assertThatThrownBy(() -> servicio.registrarMonitoreo(30L, new MonitoreoRequest(1L, new BigDecimal("20"), LocalDate.of(2027, 2, 28)), productor))
                .satisfies(ex -> assertThat(motivos(ex)).anyMatch(m -> m.startsWith("C8")));
        assertThatThrownBy(() -> servicio.registrarMonitoreo(30L, new MonitoreoRequest(1L, new BigDecimal("20"), LocalDate.of(2027, 6, 2)), productor))
                .satisfies(ex -> assertThat(motivos(ex)).anyMatch(m -> m.startsWith("C8")));
        verify(monitoreos, never()).save(any());
    }

    @Test
    void planificarSuperpuestaInformaC1() {
        delLote.add(existente(11L, "2026-11-01", "2027-03-31", EstadoCampania.SEMBRADA));

        assertThatThrownBy(() -> servicio.planificar(3L, new CampaniaRequest(1L, new BigDecimal("10"), LocalDate.of(2026, 12, 1)), productor))
                .isInstanceOf(ReglaNegocioException.class)
                .satisfies(ex -> assertThat(((ReglaNegocioException) ex).getMotivos()).anyMatch(m -> m.startsWith("C1")));
    }

    // ---- dos cultivos en el lote (lote de 80 ha con 70 ha de soja sembrada)
    private static final CultivoResumenResponse MAIZ = new CultivoResumenResponse(2L, "Maíz", "Gramínea", 160, 9, 12);

    @Test
    void sePuedePlanificarOtroCultivoAlMismoTiempoSiEntraEnElLote() {
        delLote.add(existente(11L, "2026-11-01", "2027-03-31", EstadoCampania.SEMBRADA));
        when(tratamientos.obtenerCultivo(2L)).thenReturn(MAIZ);

        CampaniaResponse r = servicio.planificar(3L, new CampaniaRequest(2L, new BigDecimal("10"), LocalDate.of(2026, 12, 1)), productor);

        assertThat(r.estado()).isEqualTo(EstadoCampania.PLANIFICADA);
        assertThat(r.cultivoId()).isEqualTo(2L);
    }

    @Test
    void otroCultivoQueNoEntraEnLoQueQuedaDelLoteEsC1() {
        delLote.add(existente(11L, "2026-11-01", "2027-03-31", EstadoCampania.SEMBRADA));
        when(tratamientos.obtenerCultivo(2L)).thenReturn(MAIZ);

        assertThatThrownBy(() -> servicio.planificar(3L, new CampaniaRequest(2L, new BigDecimal("11"), LocalDate.of(2026, 12, 1)), productor))
                .satisfies(ex -> assertThat(motivos(ex)).anyMatch(m -> m.startsWith("C1") && m.contains("libres: 10 ha")));
        verify(campanias, never()).save(any());
    }

    // ---- rendimiento
    @Test
    void laCosechaConKgCalculaElRendimiento() {
        guardada(existente(30L, "2026-11-01", "2027-03-31", EstadoCampania.SEMBRADA));

        CampaniaResponse r = servicio.registrarCosecha(30L, new CosechaRequest(LocalDate.of(2027, 4, 1), new BigDecimal("245000")), productor);

        assertThat(r.kgCosechados()).isEqualByComparingTo("245000");
        assertThat(r.rendimientoKgHa()).isEqualByComparingTo("3500.0");   // 245.000 kg / 70 ha
    }

    @Test
    void laCosechaSinKgNoTieneRendimiento() {
        guardada(existente(30L, "2026-11-01", "2027-03-31", EstadoCampania.SEMBRADA));

        CampaniaResponse r = servicio.registrarCosecha(30L, new CosechaRequest(LocalDate.of(2027, 4, 1)), productor);

        assertThat(r.rendimientoKgHa()).isNull();
    }

    // ---- alertas de monitoreo (umbral 20 %)
    @Test
    void unMonitoreoQueLlegaAlUmbralEsAlerta() {
        guardada(existente(30L, "2027-03-01", "2027-07-29", EstadoCampania.SEMBRADA));

        var r = servicio.registrarMonitoreo(30L, new MonitoreoRequest(1L, new BigDecimal("20"), null), productor);

        assertThat(r.alerta()).isTrue();
        assertThat(r.mensaje()).contains("Se recomienda registrar una aplicación");
    }

    @Test
    void unMonitoreoPorDebajoDelUmbralNoEsAlerta() {
        guardada(existente(30L, "2027-03-01", "2027-07-29", EstadoCampania.SEMBRADA));

        var r = servicio.registrarMonitoreo(30L, new MonitoreoRequest(1L, new BigDecimal("19.99"), null), productor);

        assertThat(r.alerta()).isFalse();
    }
}
