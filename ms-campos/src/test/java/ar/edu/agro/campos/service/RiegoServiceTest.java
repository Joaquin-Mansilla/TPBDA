package ar.edu.agro.campos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ar.edu.agro.campos.client.TratamientosClient;
import ar.edu.agro.campos.dto.CamposDtos.LluviaRequest;
import ar.edu.agro.campos.dto.CamposDtos.RiegoRequest;
import ar.edu.agro.campos.model.Campania;
import ar.edu.agro.campos.model.EstadoCampania;
import ar.edu.agro.campos.model.EstadoRiego;
import ar.edu.agro.campos.model.Establecimiento;
import ar.edu.agro.campos.model.Lluvia;
import ar.edu.agro.campos.model.Lote;
import ar.edu.agro.campos.model.OrigenAgua;
import ar.edu.agro.campos.model.Riego;
import ar.edu.agro.campos.repository.LluviaRepository;
import ar.edu.agro.campos.repository.RiegoRepository;
import ar.edu.agro.commons.contract.CultivoResumenResponse;
import ar.edu.agro.commons.error.ReglaNegocioException;
import ar.edu.agro.commons.security.Roles;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

/** Riego (R1) con "hoy" = 20/11/2026 y una soja sembrada el 10/11 que pide 25 mm cada 7 días. */
class RiegoServiceTest {

    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-11-20T15:00:00Z"), ZoneId.of("America/Argentina/Buenos_Aires"));
    private static final LocalDate HOY = LocalDate.of(2026, 11, 20);
    private static final CultivoResumenResponse SOJA =
            new CultivoResumenResponse(1L, "Soja", "Leguminosa", 150, 10, 12, 7, new BigDecimal("25"));
    private static final CultivoResumenResponse TRIGO = new CultivoResumenResponse(3L, "Trigo", "Gramínea", 180, 5, 7);

    private final Usuario operario = new Usuario("op-1", Set.of(Roles.OPERARIO), "operario1 (Oscar Operario)");
    private final Usuario otroOperario = new Usuario("op-9", Set.of(Roles.OPERARIO));

    private RiegoRepository repo;
    private LluviaRepository lluvias;
    private TratamientosClient tratamientos;
    private RiegoService servicio;
    private Lote lote;
    private Campania campania;
    private final List<Riego> guardados = new ArrayList<>();
    private final List<Lluvia> delLote = new ArrayList<>();

    @BeforeEach
    void preparar() {
        repo = mock(RiegoRepository.class);
        lluvias = mock(LluviaRepository.class);
        tratamientos = mock(TratamientosClient.class);
        CampaniaService campanias = mock(CampaniaService.class);
        LoteService lotes = mock(LoteService.class);

        Establecimiento e = new Establecimiento();
        e.setId(1L);
        e.setProductorSub("prod-1");
        e.setOperarios(new HashSet<>(Set.of("op-1")));
        lote = new Lote();
        lote.setId(2L);
        lote.setEstablecimiento(e);
        campania = new Campania();
        campania.setId(5L);
        campania.setLote(lote);
        campania.setCultivoId(1L);
        campania.setSuperficieSembradaHa(new BigDecimal("40"));
        campania.setFechaSiembra(LocalDate.of(2026, 11, 10));
        campania.setCosechaEstimada(LocalDate.of(2027, 4, 9));

        when(campanias.buscar(5L)).thenReturn(campania);
        when(lotes.buscar(2L)).thenReturn(lote);
        when(tratamientos.obtenerCultivo(1L)).thenReturn(SOJA);
        when(repo.findByCampaniaIdOrderByFechaAsc(5L)).thenReturn(guardados);
        when(repo.save(any(Riego.class))).thenAnswer(i -> {
            Riego r = i.getArgument(0);
            r.setId(70L);
            return r;
        });
        when(lluvias.findByLoteIdOrderByFechaAscIdAsc(2L)).thenReturn(delLote);
        when(lluvias.save(any(Lluvia.class))).thenAnswer(i -> {
            Lluvia l = i.getArgument(0);
            l.setId(80L);
            return l;
        });
        servicio = new RiegoService(repo, lluvias, campanias, lotes, new AccesoService(), tratamientos, RELOJ, 2);
    }

    private void sembrada() {
        campania.sembrar(LocalDate.of(2026, 11, 10), LocalDate.of(2027, 4, 9));
    }

    private void regado(String fecha, String mm) {
        Riego r = new Riego();
        r.setCampania(campania);
        r.setFecha(LocalDate.parse(fecha));
        r.setLaminaMm(new BigDecimal(mm));
        r.setRegistradoPorSub("op-1");
        guardados.add(r);
    }

    private void llovio(LocalDate fecha, String mm) {
        Lluvia l = new Lluvia();
        l.setLote(lote);
        l.setFecha(fecha);
        l.setMm(new BigDecimal(mm));
        l.setRegistradoPorSub("op-1");
        delLote.add(l);
    }

    // ---- riegos
    @Test
    void elOperarioAsignadoRegistraUnRiegoSinFechaQueEsDeHoy() {
        sembrada();

        var r = servicio.registrar(5L, new RiegoRequest(null, new BigDecimal("25")), operario);

        assertThat(r.fecha()).isEqualTo(HOY);
        assertThat(r.registradoPorSub()).isEqualTo("op-1");
        assertThat(r.registradoPorNombre()).isEqualTo("operario1 (Oscar Operario)");
    }

    @Test
    void noSeRiegaUnaCampaniaQueNoEstaSembradaC5() {
        assertThatThrownBy(() -> servicio.registrar(5L, new RiegoRequest(HOY, new BigDecimal("25")), operario))
                .isInstanceOf(ReglaNegocioException.class)
                .satisfies(ex -> assertThat(((ReglaNegocioException) ex).getMotivos()).anyMatch(m -> m.startsWith("C5")));
        verify(repo, never()).save(any());
    }

    @Test
    void unRiegoAnteriorALaSiembraOFuturoEsC8() {
        sembrada();

        assertThatThrownBy(() -> servicio.registrar(5L, new RiegoRequest(LocalDate.of(2026, 11, 9), new BigDecimal("25")), operario))
                .satisfies(ex -> assertThat(((ReglaNegocioException) ex).getMotivos()).anyMatch(m -> m.startsWith("C8")));
        assertThatThrownBy(() -> servicio.registrar(5L, new RiegoRequest(HOY.plusDays(1), new BigDecimal("25")), operario))
                .satisfies(ex -> assertThat(((ReglaNegocioException) ex).getMotivos()).anyMatch(m -> m.startsWith("C8")));
    }

    @Test
    void unOperarioNoAsignadoNoRiega() {
        sembrada();

        assertThatThrownBy(() -> servicio.registrar(5L, new RiegoRequest(HOY, new BigDecimal("25")), otroOperario))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ---- lluvias cargadas a mano
    @Test
    void elOperarioCargaLaLluviaDeHoyEnElLote() {
        var l = servicio.registrarLluvia(2L, new LluviaRequest(null, new BigDecimal("6.4")), operario);

        assertThat(l.fecha()).isEqualTo(HOY);
        assertThat(l.loteId()).isEqualTo(2L);
        assertThat(l.mm()).isEqualByComparingTo("6.4");
        assertThat(l.registradoPorNombre()).isEqualTo("operario1 (Oscar Operario)");
    }

    @Test
    void ceroMmDejaRegistradoQueNoLlovio() {
        var l = servicio.registrarLluvia(2L, new LluviaRequest(HOY, BigDecimal.ZERO), operario);

        assertThat(l.mm()).isEqualByComparingTo("0");
    }

    @Test
    void noSeCargaLluviaDeUnaFechaFuturaC8() {
        assertThatThrownBy(() -> servicio.registrarLluvia(2L, new LluviaRequest(HOY.plusDays(1), BigDecimal.ONE), operario))
                .satisfies(ex -> assertThat(((ReglaNegocioException) ex).getMotivos()).anyMatch(m -> m.startsWith("C8")));
        verify(lluvias, never()).save(any());
    }

    @Test
    void unOperarioNoAsignadoNoCargaLluvia() {
        assertThatThrownBy(() -> servicio.registrarLluvia(2L, new LluviaRequest(HOY, BigDecimal.ONE), otroOperario))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ---- estado de riego
    @Test
    void sinAguaDesdeLaSiembraHace10DiasEstaAtrasado() {
        sembrada();

        var r = servicio.consultarEstado(5L, operario);

        assertThat(r.estado()).isEqualTo(EstadoRiego.ATRASADO);
        assertThat(r.diasSinAgua()).isEqualTo(10);
        assertThat(r.lluviaDesdeSiembraMm()).isEqualByComparingTo("0");
    }

    @Test
    void conUnRiegoDeHoyEstaAlDia() {
        sembrada();
        regado("2026-11-20", "25");

        var r = servicio.consultarEstado(5L, operario);

        assertThat(r.estado()).isEqualTo(EstadoRiego.AL_DIA);
        assertThat(r.origenUltimaAgua()).isEqualTo(OrigenAgua.RIEGO);
        assertThat(r.proximoRiego()).isEqualTo(LocalDate.of(2026, 11, 27));
        assertThat(r.riegosRegistrados()).isEqualTo(1);
        assertThat(r.mmRegadosTotal()).isEqualByComparingTo("25");
    }

    @Test
    void laLluviaCargadaEnElLoteCuentaComoRiego() {
        sembrada();
        llovio(LocalDate.of(2026, 11, 18), "32.5");

        var r = servicio.consultarEstado(5L, operario);

        assertThat(r.estado()).isEqualTo(EstadoRiego.AL_DIA);
        assertThat(r.origenUltimaAgua()).isEqualTo(OrigenAgua.LLUVIA);
        assertThat(r.lluviaDesdeSiembraMm()).isEqualByComparingTo("32.5");
    }

    @Test
    void dosCargasDelMismoDiaSeSuman() {
        sembrada();
        llovio(LocalDate.of(2026, 11, 18), "15");
        llovio(LocalDate.of(2026, 11, 18), "10");

        assertThat(servicio.consultarEstado(5L, operario).origenUltimaAgua()).isEqualTo(OrigenAgua.LLUVIA);
    }

    @Test
    void diceCuantoFaltaRegarYRegandoEsoQuedaAlDia() {
        // sembrada hoy (20/11) con 6,4 mm de lluvia: le faltan 18,6 mm para el riego de asiento
        campania.sembrar(HOY, LocalDate.of(2027, 4, 19));
        llovio(HOY, "6.4");

        var antes = servicio.consultarEstado(5L, operario);
        assertThat(antes.estado()).isEqualTo(EstadoRiego.TOCA_REGAR);
        assertThat(antes.mmFaltantes()).isEqualByComparingTo("18.6");

        regado("2026-11-20", antes.mmFaltantes().toPlainString());
        var despues = servicio.consultarEstado(5L, operario);

        assertThat(despues.estado()).isEqualTo(EstadoRiego.AL_DIA);
        assertThat(despues.origenUltimaAgua()).isEqualTo(OrigenAgua.RIEGO);
        assertThat(despues.mmFaltantes()).isEqualByComparingTo("25");   // el próximo riego vuelve a pedir los 25 mm
    }

    @Test
    void unCultivoASecanoNoRequiereRiego() {
        sembrada();
        when(tratamientos.obtenerCultivo(1L)).thenReturn(TRIGO);

        var r = servicio.consultarEstado(5L, operario);

        assertThat(r.estado()).isEqualTo(EstadoRiego.NO_REQUIERE);
    }

    @Test
    void unaCampaniaPlanificadaNoSeCalculaNiSeConsultaANadie() {
        var r = servicio.consultarEstado(5L, operario);

        assertThat(r.estado()).isEqualTo(EstadoRiego.NO_SEMBRADA);
        assertThat(campania.getEstado()).isEqualTo(EstadoCampania.PLANIFICADA);
        verifyNoInteractions(tratamientos);
    }

    // ---- mensajes
    private String mensaje(LocalDate siembra, Map<LocalDate, BigDecimal> riegos, Map<LocalDate, BigDecimal> lluvia) {
        var res = ReglasRiego.calcular(siembra, riegos, lluvia, 7, new BigDecimal("25"), 2, HOY);
        return servicio.mensaje(res, SOJA);
    }

    @Test
    void recienSembradaConPocaLluviaTocaRegarYDiceCuantoFalta() {
        String m = mensaje(HOY, Map.of(), Map.of(HOY, new BigDecimal("6.40")));

        assertThat(m).isEqualTo("Toca regar: sembrada el 2026-11-20 y todavía no recibió el primer riego (25 mm). "
                + "Desde la siembra juntó 6,4 mm entre riego y lluvia: le faltan 18,6 mm para completar un riego");
    }

    @Test
    void sinRiegoDeAsientoPasadaLaToleranciaDiceQueEstaAtrasado() {
        String m = mensaje(LocalDate.of(2026, 11, 12), Map.of(), Map.of());

        assertThat(m).isEqualTo("Riego atrasado: sembrada el 2026-11-12 y todavía no recibió el primer riego (25 mm). "
                + "Pasaron 8 días y la tolerancia es de 2");
    }

    @Test
    void unaLluviaQueContoComoRiegoLoDice() {
        String m = mensaje(LocalDate.of(2026, 11, 10), Map.of(), Map.of(LocalDate.of(2026, 11, 16), new BigDecimal("27.8")));

        assertThat(m).isEqualTo("Al día: el 2026-11-16 la lluvia alcanzó los 25 mm y contó como riego. Próximo riego: 2026-11-23");
    }
}
