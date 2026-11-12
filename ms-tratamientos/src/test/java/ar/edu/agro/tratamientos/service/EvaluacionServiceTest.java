package ar.edu.agro.tratamientos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.agro.commons.contract.AplicacionPreviaDto;
import ar.edu.agro.commons.contract.ClaseTextura;
import ar.edu.agro.commons.contract.EvaluacionRequest;
import ar.edu.agro.commons.contract.EvaluacionResponse;
import ar.edu.agro.commons.contract.MotivoDto;
import ar.edu.agro.commons.contract.ResultadoEvaluacion;
import ar.edu.agro.commons.contract.SueloDto;
import ar.edu.agro.commons.contract.UbicacionDto;
import ar.edu.agro.commons.error.RecursoNoEncontradoException;
import ar.edu.agro.tratamientos.model.Agroquimico;
import ar.edu.agro.tratamientos.model.AjusteSuelo;
import ar.edu.agro.tratamientos.model.Cultivo;
import ar.edu.agro.tratamientos.model.Evaluacion;
import ar.edu.agro.tratamientos.model.Plaga;
import ar.edu.agro.tratamientos.model.RestriccionTerritorial;
import ar.edu.agro.tratamientos.repository.AgroquimicoRepository;
import ar.edu.agro.tratamientos.repository.AjusteSueloRepository;
import ar.edu.agro.tratamientos.repository.CultivoRepository;
import ar.edu.agro.tratamientos.repository.EvaluacionRepository;
import ar.edu.agro.tratamientos.repository.HabilitacionRepository;
import ar.edu.agro.tratamientos.repository.PlagaRepository;
import ar.edu.agro.tratamientos.repository.RestriccionTerritorialRepository;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** La decisión de ms-tratamientos (F11) con el contrato real de agro-commons. Sin base de datos. */
class EvaluacionServiceTest {

    private static final LocalDate FECHA = LocalDate.of(2026, 11, 20);
    private static final LocalDate COSECHA = LocalDate.of(2027, 3, 31);

    private AgroquimicoRepository agroquimicos;
    private CultivoRepository cultivos;
    private PlagaRepository plagas;
    private HabilitacionRepository habilitaciones;
    private AjusteSueloRepository ajustes;
    private RestriccionTerritorialRepository restricciones;
    private EvaluacionRepository evaluaciones;
    private EvaluacionService servicio;

    @BeforeEach
    void preparar() {
        agroquimicos = mock(AgroquimicoRepository.class);
        cultivos = mock(CultivoRepository.class);
        plagas = mock(PlagaRepository.class);
        habilitaciones = mock(HabilitacionRepository.class);
        ajustes = mock(AjusteSueloRepository.class);
        restricciones = mock(RestriccionTerritorialRepository.class);
        evaluaciones = mock(EvaluacionRepository.class);
        servicio = new EvaluacionService(agroquimicos, cultivos, plagas, habilitaciones, ajustes, restricciones,
                evaluaciones, JsonMapper.builder().findAndAddModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build());

        // Producto: dosis 0,5 a 1,5 l/ha, envase de 10 l a $8000, carencia 21 días, intervalo 14 días
        Agroquimico agro = new Agroquimico();
        agro.setId(1L);
        agro.setNombre("Clorpirifos Plus");
        agro.setDosisMinHa(new BigDecimal("0.5"));
        agro.setDosisMaxHa(new BigDecimal("1.5"));
        agro.setContenidoEnvase(new BigDecimal("10"));
        agro.setPrecioEnvase(new BigDecimal("8000"));
        agro.setDiasCarencia(21);
        agro.setIntervaloMinDias(14);
        when(agroquimicos.findById(1L)).thenReturn(Optional.of(agro));

        Cultivo cultivo = new Cultivo();
        cultivo.setId(1L);
        when(cultivos.findById(1L)).thenReturn(Optional.of(cultivo));
        Plaga plaga = new Plaga();
        plaga.setId(1L);
        when(plagas.findById(1L)).thenReturn(Optional.of(plaga));

        when(habilitaciones.existsByAgroquimicoIdAndCultivoIdAndPlagaId(1L, 1L, 1L)).thenReturn(true);
        when(ajustes.findByAgroquimicoIdAndClaseTextura(any(), any())).thenReturn(Optional.empty());
        when(restricciones.findByAgroquimicoId(1L)).thenReturn(List.of());
        when(evaluaciones.save(any(Evaluacion.class))).thenAnswer(inv -> {
            Evaluacion ev = inv.getArgument(0);
            ev.setId(55L);
            return ev;
        });
    }

    private SueloDto suelo() {
        return new SueloDto(ClaseTextura.FRANCO, new BigDecimal("25"), new BigDecimal("40"), new BigDecimal("35"),
                new BigDecimal("6.5"), new BigDecimal("15"));
    }

    private UbicacionDto ubicacion() {
        return new UbicacionDto("Córdoba", "Marcos Juárez", null);
    }

    private EvaluacionRequest pedido(String dosis, String superficie, LocalDate fecha, SueloDto suelo,
                                     UbicacionDto ubicacion, List<AplicacionPreviaDto> previas) {
        return new EvaluacionRequest(1L, 1L, 1L, new BigDecimal(dosis), new BigDecimal(superficie), fecha, COSECHA,
                suelo, ubicacion, previas);
    }

    private EvaluacionRequest pedidoValido() {
        return pedido("1.0", "40", FECHA, suelo(), ubicacion(), List.of());
    }

    private List<String> reglas(EvaluacionResponse r) {
        return r.motivos().stream().map(MotivoDto::regla).toList();
    }

    @Test
    void unaAplicacionQueCumpleTodasLasReglasEsViableYCalculaElCosto() {
        EvaluacionResponse r = servicio.evaluar(pedidoValido(), "op-1");

        assertThat(r.id()).isEqualTo(55L);
        assertThat(r.resultado()).isEqualTo(ResultadoEvaluacion.VIABLE);
        assertThat(r.motivos()).isEmpty();
        assertThat(r.fechaSugerida()).isNull();
        assertThat(r.dosisTotal()).isEqualByComparingTo("40");     // 1,0 l/ha x 40 ha
        assertThat(r.envases()).isEqualTo(4);                      // 40 / 10, redondeado hacia arriba
        assertThat(r.costo()).isEqualByComparingTo("32000");       // 4 x 8000
    }

    @Test
    void losEnvasesSeRedondeanHaciaArribaTambienEnUnRechazo() {
        when(habilitaciones.existsByAgroquimicoIdAndCultivoIdAndPlagaId(1L, 1L, 1L)).thenReturn(false);

        EvaluacionResponse r = servicio.evaluar(pedido("1.0", "45", FECHA, suelo(), ubicacion(), List.of()), "op-1");

        assertThat(r.resultado()).isEqualTo(ResultadoEvaluacion.NO_RECOMENDADO);
        assertThat(r.envases()).isEqualTo(5);                      // 45 / 10 = 4,5
        assertThat(r.costo()).isEqualByComparingTo("40000");
    }

    @Test
    void siSoloFallaElIntervaloMinimoSeReprogramaConLaFechaSugerida() {
        // última aplicación el 10/11 + 14 días = primera fecha posible el 24/11
        var previas = List.of(new AplicacionPreviaDto(LocalDate.of(2026, 11, 10)));

        EvaluacionResponse r = servicio.evaluar(pedido("1.0", "40", FECHA, suelo(), ubicacion(), previas), "op-1");

        assertThat(r.resultado()).isEqualTo(ResultadoEvaluacion.REPROGRAMAR);
        assertThat(r.fechaSugerida()).isEqualTo(LocalDate.of(2026, 11, 24));
        assertThat(reglas(r)).containsExactly("T3");
    }

    @Test
    void conVariasAplicacionesPreviasUsaLaUltima() {
        var previas = List.of(new AplicacionPreviaDto(LocalDate.of(2026, 9, 1)),
                new AplicacionPreviaDto(LocalDate.of(2026, 11, 10)),
                new AplicacionPreviaDto(LocalDate.of(2026, 10, 1)));

        EvaluacionResponse r = servicio.evaluar(pedido("1.0", "40", FECHA, suelo(), ubicacion(), previas), "op-1");

        assertThat(r.fechaSugerida()).isEqualTo(LocalDate.of(2026, 11, 24));
    }

    @Test
    void unaAplicacionPreviaMasAntiguaQueElIntervaloNoMolesta() {
        var previas = List.of(new AplicacionPreviaDto(LocalDate.of(2026, 11, 6)));    // 6/11 + 14 = 20/11: justo

        EvaluacionResponse r = servicio.evaluar(pedido("1.0", "40", FECHA, suelo(), ubicacion(), previas), "op-1");

        assertThat(r.resultado()).isEqualTo(ResultadoEvaluacion.VIABLE);
    }

    @Test
    void unProductoNoHabilitadoParaElCultivoYLaPlagaEsNoRecomendadoPorT1() {
        when(habilitaciones.existsByAgroquimicoIdAndCultivoIdAndPlagaId(1L, 1L, 1L)).thenReturn(false);

        EvaluacionResponse r = servicio.evaluar(pedidoValido(), "op-1");

        assertThat(r.resultado()).isEqualTo(ResultadoEvaluacion.NO_RECOMENDADO);
        assertThat(reglas(r)).containsExactly("T1");
        assertThat(r.fechaSugerida()).isNull();
    }

    @Test
    void unaDosisMayorQueLaMaximaAjustadaPorElSueloEsNoRecomendadoPorT2() {
        AjusteSuelo ajuste = new AjusteSuelo();
        ajuste.setFactorDosisMax(new BigDecimal("0.8"));                // máxima 1,5 x 0,8 = 1,2
        when(ajustes.findByAgroquimicoIdAndClaseTextura(1L, ClaseTextura.FRANCO)).thenReturn(Optional.of(ajuste));

        EvaluacionResponse r = servicio.evaluar(pedido("1.3", "40", FECHA, suelo(), ubicacion(), List.of()), "op-1");

        assertThat(r.resultado()).isEqualTo(ResultadoEvaluacion.NO_RECOMENDADO);
        assertThat(reglas(r)).containsExactly("T2");
    }

    @Test
    void unaCarenciaQueSuperaLaCosechaEstimadaEsNoRecomendadoPorT4() {
        // 20/03/2027 + 21 días de carencia = 10/04/2027, después de la cosecha del 31/03/2027
        EvaluacionResponse r = servicio.evaluar(
                pedido("1.0", "40", LocalDate.of(2027, 3, 20), suelo(), ubicacion(), List.of()), "op-1");

        assertThat(r.resultado()).isEqualTo(ResultadoEvaluacion.NO_RECOMENDADO);
        assertThat(reglas(r)).containsExactly("T4");
    }

    @Test
    void unProductoRestringidoEnLaProvinciaDelLoteEsNoRecomendadoPorT5() {
        RestriccionTerritorial restriccion = new RestriccionTerritorial();
        restriccion.setProvincia("Córdoba");
        when(restricciones.findByAgroquimicoId(1L)).thenReturn(List.of(restriccion));

        EvaluacionResponse r = servicio.evaluar(pedidoValido(), "op-1");

        assertThat(r.resultado()).isEqualTo(ResultadoEvaluacion.NO_RECOMENDADO);
        assertThat(reglas(r)).containsExactly("T5");
    }

    @Test
    void sinSueloLaEvaluacionPideRevisionManual() {
        EvaluacionResponse r = servicio.evaluar(pedido("1.0", "40", FECHA, null, ubicacion(), List.of()), "op-1");

        assertThat(r.resultado()).isEqualTo(ResultadoEvaluacion.REVISION_MANUAL);
        assertThat(reglas(r)).containsExactly("L1");
    }

    @Test
    void sinUbicacionLaEvaluacionPideRevisionManual() {
        EvaluacionResponse r = servicio.evaluar(pedido("1.0", "40", FECHA, suelo(), null, List.of()), "op-1");

        assertThat(r.resultado()).isEqualTo(ResultadoEvaluacion.REVISION_MANUAL);
        assertThat(reglas(r)).containsExactly("L1");
    }

    @Test
    void unaUbicacionSinProvinciaTambienEsRevisionManual() {
        EvaluacionResponse r = servicio.evaluar(
                pedido("1.0", "40", FECHA, suelo(), new UbicacionDto(" ", null, null), List.of()), "op-1");

        assertThat(r.resultado()).isEqualTo(ResultadoEvaluacion.REVISION_MANUAL);
    }

    @Test
    void siFaltanDatosPeroUnaReglaYaFallaEsNoRecomendadoNoRevisionManual() {
        when(habilitaciones.existsByAgroquimicoIdAndCultivoIdAndPlagaId(1L, 1L, 1L)).thenReturn(false);

        EvaluacionResponse r = servicio.evaluar(pedido("1.0", "40", FECHA, null, null, List.of()), "op-1");

        assertThat(r.resultado()).isEqualTo(ResultadoEvaluacion.NO_RECOMENDADO);
        assertThat(reglas(r)).containsExactly("T1");
    }

    @Test
    void siFallanVariasReglasEsNoRecomendadoYNoSeSugiereFecha() {
        var previas = List.of(new AplicacionPreviaDto(LocalDate.of(2026, 11, 10)));          // T3
        when(habilitaciones.existsByAgroquimicoIdAndCultivoIdAndPlagaId(1L, 1L, 1L)).thenReturn(false);   // T1

        EvaluacionResponse r = servicio.evaluar(pedido("1.0", "40", FECHA, suelo(), ubicacion(), previas), "op-1");

        assertThat(r.resultado()).isEqualTo(ResultadoEvaluacion.NO_RECOMENDADO);
        assertThat(reglas(r)).containsExactlyInAnyOrder("T1", "T3");
        assertThat(r.fechaSugerida()).isNull();
    }

    @Test
    void unAgroquimicoCultivoOPlagaInexistenteEs404YNoSeGuardaNada() {
        when(agroquimicos.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.evaluar(pedidoValido(), "op-1")).isInstanceOf(RecursoNoEncontradoException.class);

        preparar();
        when(cultivos.findById(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> servicio.evaluar(pedidoValido(), "op-1")).isInstanceOf(RecursoNoEncontradoException.class);

        preparar();
        when(plagas.findById(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> servicio.evaluar(pedidoValido(), "op-1")).isInstanceOf(RecursoNoEncontradoException.class);
        verify(evaluaciones, never()).save(any());
    }

    @Test
    void cadaDecisionGuardaUnaCopiaDelPedidoYQuienLaPidio() {
        servicio.evaluar(pedidoValido(), "op-1");

        ArgumentCaptor<Evaluacion> guardada = ArgumentCaptor.forClass(Evaluacion.class);
        verify(evaluaciones).save(guardada.capture());
        Evaluacion ev = guardada.getValue();
        assertThat(ev.getSolicitadaPorSub()).isEqualTo("op-1");
        assertThat(ev.getResultado()).isEqualTo(ResultadoEvaluacion.VIABLE);
        assertThat(ev.getSnapshotPedido()).contains("\"cultivoId\":1").contains("\"provincia\":\"Córdoba\"")
                .contains("FRANCO").contains("2026-11-20");
    }

    @Test
    void tambienSeGuardanLosRechazosConSusMotivos() {
        when(habilitaciones.existsByAgroquimicoIdAndCultivoIdAndPlagaId(1L, 1L, 1L)).thenReturn(false);

        servicio.evaluar(pedidoValido(), "op-1");

        ArgumentCaptor<Evaluacion> guardada = ArgumentCaptor.forClass(Evaluacion.class);
        verify(evaluaciones).save(guardada.capture());
        assertThat(guardada.getValue().getMotivos()).hasSize(1);
        assertThat(guardada.getValue().getMotivos().get(0).getRegla()).isEqualTo("T1");
    }
}
