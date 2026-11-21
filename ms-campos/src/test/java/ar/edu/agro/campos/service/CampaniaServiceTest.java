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
import ar.edu.agro.campos.dto.CamposDtos.PerdidaRequest;
import ar.edu.agro.campos.dto.CamposDtos.SiembraRequest;
import ar.edu.agro.campos.model.Campania;
import ar.edu.agro.campos.model.EstadoCampania;
import ar.edu.agro.campos.model.Establecimiento;
import ar.edu.agro.campos.model.Lote;
import ar.edu.agro.campos.repository.CampaniaRepository;
import ar.edu.agro.campos.repository.MonitoreoRepository;
import ar.edu.agro.commons.contract.CultivoResumenResponse;
import ar.edu.agro.commons.error.ReglaNegocioException;
import ar.edu.agro.commons.security.Roles;
import java.math.BigDecimal;
import java.time.LocalDate;
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

    private final Usuario productor = new Usuario("prod-1", Set.of(Roles.PRODUCTOR));

    private CampaniaRepository campanias;
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

        servicio = new CampaniaService(campanias, mock(MonitoreoRepository.class), lotes, new AccesoService(), tratamientos, tx);
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

        CampaniaResponse r = servicio.registrarCosecha(30L, new CosechaRequest(LocalDate.of(2027, 3, 20)), productor);

        assertThat(r.estado()).isEqualTo(EstadoCampania.COSECHADA);
        assertThat(r.fechaCosecha()).isEqualTo(LocalDate.of(2027, 3, 20));
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

    @Test
    void planificarSuperpuestaInformaC1() {
        delLote.add(existente(11L, "2026-11-01", "2027-03-31", EstadoCampania.SEMBRADA));

        assertThatThrownBy(() -> servicio.planificar(3L, new CampaniaRequest(1L, new BigDecimal("10"), LocalDate.of(2026, 12, 1)), productor))
                .isInstanceOf(ReglaNegocioException.class)
                .satisfies(ex -> assertThat(((ReglaNegocioException) ex).getMotivos()).anyMatch(m -> m.startsWith("C1")));
    }
}
