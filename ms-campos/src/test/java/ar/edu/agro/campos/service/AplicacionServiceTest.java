package ar.edu.agro.campos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ar.edu.agro.campos.client.ApiExternaException;
import ar.edu.agro.campos.client.TratamientosClient;
import ar.edu.agro.campos.dto.CamposDtos.AplicacionRequest;
import ar.edu.agro.campos.dto.CamposDtos.AplicacionResponse;
import ar.edu.agro.campos.dto.CamposDtos.AutorizacionRequest;
import ar.edu.agro.campos.model.Aplicacion;
import ar.edu.agro.campos.model.Campania;
import ar.edu.agro.campos.model.EstadoAplicacion;
import ar.edu.agro.campos.model.EstadoCampania;
import ar.edu.agro.campos.model.EstadoSuelo;
import ar.edu.agro.campos.model.Establecimiento;
import ar.edu.agro.campos.model.Lote;
import ar.edu.agro.campos.repository.AplicacionRepository;
import ar.edu.agro.commons.contract.ClaseTextura;
import ar.edu.agro.commons.contract.EvaluacionRequest;
import ar.edu.agro.commons.contract.EvaluacionResponse;
import ar.edu.agro.commons.contract.MotivoDto;
import ar.edu.agro.commons.contract.ResultadoEvaluacion;
import ar.edu.agro.commons.error.RecursoNoEncontradoException;
import ar.edu.agro.commons.error.ReglaNegocioException;
import ar.edu.agro.commons.error.ServicioNoDisponibleException;
import ar.edu.agro.commons.security.Roles;
import jakarta.validation.Validation;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;

/**
 * Flujo completo de evaluación (F07 a F09): registrar, estados pendientes y resoluciones manuales.
 * No usa base de datos ni red: el repositorio y ms-tratamientos están simulados.
 */
class AplicacionServiceTest {

    private static final LocalDate FECHA = LocalDate.of(2026, 11, 20);

    private final Usuario productor = new Usuario("prod-1", Set.of(Roles.PRODUCTOR));
    private final Usuario otroProductor = new Usuario("prod-2", Set.of(Roles.PRODUCTOR));
    private final Usuario operario = new Usuario("op-1", Set.of(Roles.OPERARIO));
    private final Usuario operarioNoAsignado = new Usuario("op-9", Set.of(Roles.OPERARIO));
    private final Usuario agronomo = new Usuario("agro-1", Set.of(Roles.AGRONOMO));

    private AplicacionRepository repo;
    private CampaniaService campanias;
    private TratamientosClient tratamientos;
    private AplicacionService servicio;

    private Lote lote;
    private Campania campania;

    @BeforeEach
    void preparar() {
        repo = mock(AplicacionRepository.class);
        campanias = mock(CampaniaService.class);
        tratamientos = mock(TratamientosClient.class);
        servicio = new AplicacionService(repo, campanias, new AccesoService(), tratamientos);

        Establecimiento e = new Establecimiento();
        e.setId(1L);
        e.setProductorSub("prod-1");
        e.setOperarios(new HashSet<>(Set.of("op-1")));

        lote = new Lote();
        lote.setId(1L);
        lote.setEstablecimiento(e);
        lote.setSuperficieHa(new BigDecimal("100"));
        lote.setEstadoSuelo(EstadoSuelo.OK);
        lote.setClaseTextura(ClaseTextura.FRANCO);
        lote.setArcillaPct(new BigDecimal("25"));
        lote.setArenaPct(new BigDecimal("40"));
        lote.setLimoPct(new BigDecimal("35"));
        lote.setPh(new BigDecimal("6.5"));
        lote.setCarbonoOrganico(new BigDecimal("15"));
        lote.setProvincia("Córdoba");
        lote.setDepartamento("Marcos Juárez");

        campania = new Campania();
        campania.setId(5L);
        campania.setLote(lote);
        campania.setCultivoId(1L);
        campania.setSuperficieSembradaHa(new BigDecimal("80"));
        campania.setFechaSiembra(LocalDate.of(2026, 11, 1));
        campania.setCosechaEstimada(LocalDate.of(2027, 3, 31));
        campania.setEstado(EstadoCampania.SEMBRADA);

        when(campanias.buscar(5L)).thenReturn(campania);
        when(repo.findByLoteYEstados(eq(1L), anyCollection())).thenReturn(List.of());
        when(repo.save(any(Aplicacion.class))).thenAnswer(inv -> {
            Aplicacion a = inv.getArgument(0);
            if (a.getId() == null) {
                a.setId(100L);
            }
            return a;
        });
    }

    // ------------------------------------------------------------------ ayudas

    private AplicacionRequest pedido() {
        return new AplicacionRequest(1L, 1L, new BigDecimal("1.000"), new BigDecimal("40"), FECHA);
    }

    private EvaluacionResponse evaluacion(ResultadoEvaluacion resultado, LocalDate fechaSugerida, List<MotivoDto> motivos) {
        return new EvaluacionResponse(55L, resultado, motivos, fechaSugerida, new BigDecimal("40.000"), 4,
                new BigDecimal("32000.00"));
    }

    private Aplicacion pendiente(Long id) {
        Aplicacion a = new Aplicacion();
        a.setId(id);
        a.setCampania(campania);
        a.setAgroquimicoId(1L);
        a.setPlagaId(1L);
        a.setDosisPorHa(new BigDecimal("1.000"));
        a.setSuperficieTratadaHa(new BigDecimal("40"));
        a.setFecha(FECHA);
        a.setEstado(EstadoAplicacion.PENDIENTE_DE_VALIDACION);
        a.setRegistradaPorSub("op-1");
        return a;
    }

    private Aplicacion hecha(Long id, Long agroquimicoId, LocalDate fecha, EstadoAplicacion estado) {
        Aplicacion a = pendiente(id);
        a.setAgroquimicoId(agroquimicoId);
        a.setFecha(fecha);
        a.setEstado(estado);
        return a;
    }

    private ReglaNegocioException reglaIncumplida(Runnable accion) {
        try {
            accion.run();
        } catch (ReglaNegocioException e) {
            return e;
        }
        throw new AssertionError("Se esperaba una ReglaNegocioException");
    }

    // ------------------------------------------------------------------ F07: registrar

    @Test
    void unaAplicacionViableQuedaRegistradaConSuCostoYSeResponde201() {
        when(tratamientos.evaluar(any())).thenReturn(evaluacion(ResultadoEvaluacion.VIABLE, null, List.of()));

        var resultado = servicio.registrar(5L, pedido(), operario);

        assertThat(resultado.status()).isEqualTo(HttpStatus.CREATED);
        assertThat(resultado.cuerpo().estado()).isEqualTo(EstadoAplicacion.REGISTRADA);
        assertThat(resultado.cuerpo().aplicacionId()).isEqualTo(100L);
        assertThat(resultado.cuerpo().evaluacionId()).isEqualTo(55L);
        assertThat(resultado.cuerpo().envases()).isEqualTo(4);
        assertThat(resultado.cuerpo().costo()).isEqualByComparingTo("32000");

        ArgumentCaptor<Aplicacion> guardada = ArgumentCaptor.forClass(Aplicacion.class);
        verify(repo).save(guardada.capture());
        assertThat(guardada.getValue().getEstado()).isEqualTo(EstadoAplicacion.REGISTRADA);
        assertThat(guardada.getValue().getRegistradaPorSub()).isEqualTo("op-1");
        assertThat(guardada.getValue().getEvaluacionId()).isEqualTo(55L);
        assertThat(guardada.getValue().getDosisTotal()).isEqualByComparingTo("40");
        assertThat(guardada.getValue().getCosto()).isEqualByComparingTo("32000");
    }

    @Test
    void reprogramarNoSeGuardaYSeResponde422ConLaFechaSugerida() {
        LocalDate sugerida = LocalDate.of(2026, 11, 25);
        when(tratamientos.evaluar(any())).thenReturn(evaluacion(ResultadoEvaluacion.REPROGRAMAR, sugerida,
                List.of(new MotivoDto("T3", "Intervalo mínimo no cumplido"))));

        var resultado = servicio.registrar(5L, pedido(), operario);

        assertThat(resultado.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(resultado.cuerpo().resultado()).isEqualTo(ResultadoEvaluacion.REPROGRAMAR);
        assertThat(resultado.cuerpo().fechaSugerida()).isEqualTo(sugerida);
        assertThat(resultado.cuerpo().aplicacionId()).isNull();
        verify(repo, never()).save(any());
    }

    @Test
    void noRecomendadoNoSeGuardaYSeResponde422ConLosMotivos() {
        when(tratamientos.evaluar(any())).thenReturn(evaluacion(ResultadoEvaluacion.NO_RECOMENDADO, null,
                List.of(new MotivoDto("T1", "No habilitado"), new MotivoDto("T5", "Restringido en Córdoba"))));

        var resultado = servicio.registrar(5L, pedido(), operario);

        assertThat(resultado.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(resultado.cuerpo().motivos()).extracting(MotivoDto::regla).containsExactly("T1", "T5");
        assertThat(resultado.cuerpo().aplicacionId()).isNull();
        verify(repo, never()).save(any());
    }

    @Test
    void sinDatosDelLoteLaEvaluacionDaRevisionManualYQuedaPendiente202() {
        when(tratamientos.evaluar(any())).thenReturn(evaluacion(ResultadoEvaluacion.REVISION_MANUAL, null,
                List.of(new MotivoDto("L1", "Faltan datos del lote"))));

        var resultado = servicio.registrar(5L, pedido(), operario);

        assertThat(resultado.status()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(resultado.cuerpo().estado()).isEqualTo(EstadoAplicacion.PENDIENTE_DE_VALIDACION);
        assertThat(resultado.cuerpo().evaluacionId()).isEqualTo(55L);
        verify(repo).save(any(Aplicacion.class));
    }

    @Test
    void siTratamientosNoRespondeQuedaPendienteYNuncaSeApruebaSola() {
        when(tratamientos.evaluar(any())).thenThrow(new ApiExternaException("Tratamientos", "no respondió"));

        var resultado = servicio.registrar(5L, pedido(), operario);

        assertThat(resultado.status()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(resultado.cuerpo().estado()).isEqualTo(EstadoAplicacion.PENDIENTE_DE_VALIDACION);
        assertThat(resultado.cuerpo().resultado()).isNull();
        assertThat(resultado.cuerpo().evaluacionId()).isNull();
        assertThat(resultado.cuerpo().mensaje()).contains("pendiente");

        ArgumentCaptor<Aplicacion> guardada = ArgumentCaptor.forClass(Aplicacion.class);
        verify(repo).save(guardada.capture());
        assertThat(guardada.getValue().getEstado()).isEqualTo(EstadoAplicacion.PENDIENTE_DE_VALIDACION);
        assertThat(guardada.getValue().getEvaluacionId()).isNull();
        assertThat(guardada.getValue().getCosto()).isNull();
    }

    @Test
    void unAgroquimicoOUnaPlagaInexistenteEs404YNoSeGuardaNada() {
        when(tratamientos.evaluar(any())).thenThrow(new RecursoNoEncontradoException("No existe el agroquímico 1"));

        assertThatThrownBy(() -> servicio.registrar(5L, pedido(), operario))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verify(repo, never()).save(any());
    }

    @Test
    void unaCampaniaQueNoEstaSembradaSeRechazaSinConsultarATratamientos() {
        campania.setEstado(EstadoCampania.PLANIFICADA);

        ReglaNegocioException e = reglaIncumplida(() -> servicio.registrar(5L, pedido(), operario));

        assertThat(e.getMotivos()).anyMatch(m -> m.startsWith("C5"));
        verifyNoInteractions(tratamientos);
        verify(repo, never()).save(any());
    }

    @Test
    void laSuperficieTratadaNoPuedeSuperarLaSembrada() {
        AplicacionRequest grande = new AplicacionRequest(1L, 1L, new BigDecimal("1"), new BigDecimal("80.01"), FECHA);

        ReglaNegocioException e = reglaIncumplida(() -> servicio.registrar(5L, grande, operario));

        assertThat(e.getMotivos()).anyMatch(m -> m.startsWith("C2"));
        verifyNoInteractions(tratamientos);
    }

    @Test
    void unOperarioNoAsignadoNoPuedeRegistrar() {
        assertThatThrownBy(() -> servicio.registrar(5L, pedido(), operarioNoAsignado))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(tratamientos);
        verify(repo, never()).save(any());
    }

    @Test
    void elProductorDuenioYElOperarioAsignadoPuedenRegistrar() {
        when(tratamientos.evaluar(any())).thenReturn(evaluacion(ResultadoEvaluacion.VIABLE, null, List.of()));

        assertThat(servicio.registrar(5L, pedido(), productor).status()).isEqualTo(HttpStatus.CREATED);
        assertThat(servicio.registrar(5L, pedido(), operario).status()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void elProductorDeOtroEstablecimientoNoPuedeRegistrar() {
        assertThatThrownBy(() -> servicio.registrar(5L, pedido(), otroProductor))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ------------------------------------------------------------------ el pedido a Tratamientos

    @Test
    void elPedidoLlevaElSueloLaUbicacionYElHistorialSoloDelMismoProducto() {
        when(tratamientos.evaluar(any())).thenReturn(evaluacion(ResultadoEvaluacion.VIABLE, null, List.of()));
        when(repo.findByLoteYEstados(eq(1L), anyCollection())).thenReturn(List.of(
                hecha(1L, 1L, LocalDate.of(2026, 11, 5), EstadoAplicacion.REGISTRADA),
                hecha(2L, 2L, LocalDate.of(2026, 11, 10), EstadoAplicacion.REGISTRADA),       // otro producto: no va
                hecha(3L, 1L, LocalDate.of(2026, 10, 1), EstadoAplicacion.AUTORIZADA_MANUALMENTE)));

        servicio.registrar(5L, pedido(), operario);

        ArgumentCaptor<EvaluacionRequest> enviado = ArgumentCaptor.forClass(EvaluacionRequest.class);
        verify(tratamientos).evaluar(enviado.capture());
        EvaluacionRequest p = enviado.getValue();
        assertThat(p.cultivoId()).isEqualTo(1L);
        assertThat(p.plagaId()).isEqualTo(1L);
        assertThat(p.agroquimicoId()).isEqualTo(1L);
        assertThat(p.dosisPorHa()).isEqualByComparingTo("1");
        assertThat(p.superficieTratadaHa()).isEqualByComparingTo("40");
        assertThat(p.fechaAplicacion()).isEqualTo(FECHA);
        assertThat(p.cosechaEstimada()).isEqualTo(LocalDate.of(2027, 3, 31));
        assertThat(p.suelo().claseTextura()).isEqualTo(ClaseTextura.FRANCO);
        assertThat(p.suelo().arcillaPct()).isEqualByComparingTo("25");
        assertThat(p.suelo().carbonoOrganicoGKg()).isEqualByComparingTo("15");
        assertThat(p.ubicacion().provincia()).isEqualTo("Córdoba");
        assertThat(p.ubicacion().departamento()).isEqualTo("Marcos Juárez");
        assertThat(p.aplicacionesPrevias()).extracting(x -> x.fecha())
                .containsExactlyInAnyOrder(LocalDate.of(2026, 11, 5), LocalDate.of(2026, 10, 1));
    }

    @Test
    void elHistorialSoloIncluyeAplicacionesQueDeVerdadSeHicieron() {
        when(tratamientos.evaluar(any())).thenReturn(evaluacion(ResultadoEvaluacion.VIABLE, null, List.of()));

        servicio.registrar(5L, pedido(), operario);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<EstadoAplicacion>> estados = ArgumentCaptor.forClass(Collection.class);
        verify(repo).findByLoteYEstados(eq(1L), estados.capture());
        assertThat(estados.getValue()).containsExactlyInAnyOrder(
                EstadoAplicacion.REGISTRADA, EstadoAplicacion.AUTORIZADA_MANUALMENTE);
    }

    @Test
    void unLoteSinSueloNiUbicacionViajaConEsosDatosEnNull() {
        lote.setEstadoSuelo(EstadoSuelo.SIN_DATOS);
        lote.setClaseTextura(null);
        lote.setProvincia(null);
        lote.setDepartamento(null);
        when(tratamientos.evaluar(any())).thenReturn(evaluacion(ResultadoEvaluacion.REVISION_MANUAL, null, List.of()));

        servicio.registrar(5L, pedido(), operario);

        ArgumentCaptor<EvaluacionRequest> enviado = ArgumentCaptor.forClass(EvaluacionRequest.class);
        verify(tratamientos).evaluar(enviado.capture());
        assertThat(enviado.getValue().suelo()).isNull();
        assertThat(enviado.getValue().ubicacion()).isNull();
    }

    // ------------------------------------------------------------------ F09: reevaluar

    @Test
    void reevaluarUnaPendienteViablePasaARegistradaYSeResponde200() {
        when(repo.findById(100L)).thenReturn(Optional.of(pendiente(100L)));
        when(tratamientos.evaluar(any())).thenReturn(evaluacion(ResultadoEvaluacion.VIABLE, null, List.of()));

        var resultado = servicio.reevaluar(100L);

        assertThat(resultado.status()).isEqualTo(HttpStatus.OK);
        assertThat(resultado.cuerpo().estado()).isEqualTo(EstadoAplicacion.REGISTRADA);
        ArgumentCaptor<Aplicacion> guardada = ArgumentCaptor.forClass(Aplicacion.class);
        verify(repo).save(guardada.capture());
        assertThat(guardada.getValue().getEstado()).isEqualTo(EstadoAplicacion.REGISTRADA);
        assertThat(guardada.getValue().getEvaluacionId()).isEqualTo(55L);
        assertThat(guardada.getValue().getCosto()).isEqualByComparingTo("32000");
    }

    @Test
    void reevaluarConTratamientosCaidoNoCambiaNadaYSeResponde503() {
        when(repo.findById(100L)).thenReturn(Optional.of(pendiente(100L)));
        when(tratamientos.evaluar(any())).thenThrow(new ApiExternaException("Tratamientos", "no respondió"));

        assertThatThrownBy(() -> servicio.reevaluar(100L)).isInstanceOf(ServicioNoDisponibleException.class);
        verify(repo, never()).save(any());
    }

    @Test
    void reevaluarSiSiguenFaltandoDatosContinuaPendiente202() {
        when(repo.findById(100L)).thenReturn(Optional.of(pendiente(100L)));
        when(tratamientos.evaluar(any())).thenReturn(evaluacion(ResultadoEvaluacion.REVISION_MANUAL, null, List.of()));

        var resultado = servicio.reevaluar(100L);

        assertThat(resultado.status()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(resultado.cuerpo().estado()).isEqualTo(EstadoAplicacion.PENDIENTE_DE_VALIDACION);
    }

    @Test
    void reevaluarSiLaEvaluacionNoLaRecomiendaContinuaPendienteYGuardaLaUltimaEvaluacion() {
        Aplicacion a = pendiente(100L);
        when(repo.findById(100L)).thenReturn(Optional.of(a));
        when(tratamientos.evaluar(any())).thenReturn(evaluacion(ResultadoEvaluacion.NO_RECOMENDADO, null,
                List.of(new MotivoDto("T2", "Dosis fuera de rango"))));

        var resultado = servicio.reevaluar(100L);

        assertThat(resultado.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(resultado.cuerpo().estado()).isEqualTo(EstadoAplicacion.PENDIENTE_DE_VALIDACION);
        assertThat(resultado.cuerpo().motivos()).extracting(MotivoDto::regla).containsExactly("T2");
        assertThat(a.getEstado()).isEqualTo(EstadoAplicacion.PENDIENTE_DE_VALIDACION);
        assertThat(a.getEvaluacionId()).isEqualTo(55L);
    }

    @Test
    void laReevaluacionNoCuentaLaPropiaAplicacionEnElHistorial() {
        Aplicacion a = pendiente(100L);
        when(repo.findById(100L)).thenReturn(Optional.of(a));
        when(repo.findByLoteYEstados(eq(1L), anyCollection())).thenReturn(List.of(
                hecha(100L, 1L, FECHA, EstadoAplicacion.REGISTRADA),                             // ella misma
                hecha(7L, 1L, LocalDate.of(2026, 11, 1), EstadoAplicacion.REGISTRADA)));
        when(tratamientos.evaluar(any())).thenReturn(evaluacion(ResultadoEvaluacion.VIABLE, null, List.of()));

        servicio.reevaluar(100L);

        ArgumentCaptor<EvaluacionRequest> enviado = ArgumentCaptor.forClass(EvaluacionRequest.class);
        verify(tratamientos).evaluar(enviado.capture());
        assertThat(enviado.getValue().aplicacionesPrevias()).extracting(x -> x.fecha())
                .containsExactly(LocalDate.of(2026, 11, 1));
    }

    @Test
    void soloSeReevaluaUnaPendiente() {
        Aplicacion registrada = pendiente(100L);
        registrada.setEstado(EstadoAplicacion.REGISTRADA);
        when(repo.findById(100L)).thenReturn(Optional.of(registrada));

        ReglaNegocioException e = reglaIncumplida(() -> servicio.reevaluar(100L));

        assertThat(e.getMotivos()).anyMatch(m -> m.startsWith("A1"));
        verifyNoInteractions(tratamientos);
    }

    @Test
    void reevaluarUnaAplicacionInexistenteEs404() {
        when(repo.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.reevaluar(999L)).isInstanceOf(RecursoNoEncontradoException.class);
    }

    // ------------------------------------------------------------------ F09: autorizar

    @Test
    void autorizarUnaPendienteLaPasaAAutorizadaManualmenteConElAgronomoYLaJustificacion() {
        when(repo.findById(100L)).thenReturn(Optional.of(pendiente(100L)));

        AplicacionResponse r = servicio.autorizar(100L, new AutorizacionRequest("  Revisé el lote en campo  "), agronomo);

        assertThat(r.estado()).isEqualTo(EstadoAplicacion.AUTORIZADA_MANUALMENTE);
        assertThat(r.justificacion()).isEqualTo("Revisé el lote en campo");
        assertThat(r.autorizadaPorSub()).isEqualTo("agro-1");
        assertThat(r.registradaPorSub()).isEqualTo("op-1");
        verify(repo).save(any(Aplicacion.class));
        verifyNoInteractions(tratamientos);
    }

    @Test
    void noSeAutorizaUnaAplicacionQueNoEstaPendiente() {
        Aplicacion registrada = pendiente(100L);
        registrada.setEstado(EstadoAplicacion.REGISTRADA);
        when(repo.findById(100L)).thenReturn(Optional.of(registrada));

        ReglaNegocioException e = reglaIncumplida(
                () -> servicio.autorizar(100L, new AutorizacionRequest("ok"), agronomo));

        assertThat(e.getMotivos()).anyMatch(m -> m.startsWith("A1"));
        verify(repo, never()).save(any());
    }

    @Test
    void unaAplicacionYaAutorizadaNoSeAutorizaDosVeces() {
        Aplicacion autorizada = pendiente(100L);
        autorizada.setEstado(EstadoAplicacion.AUTORIZADA_MANUALMENTE);
        when(repo.findById(100L)).thenReturn(Optional.of(autorizada));

        assertThatThrownBy(() -> servicio.autorizar(100L, new AutorizacionRequest("otra vez"), agronomo))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    void autorizarUnaAplicacionInexistenteEs404() {
        when(repo.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.autorizar(999L, new AutorizacionRequest("ok"), agronomo))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void laJustificacionEsObligatoria() {
        var validador = Validation.buildDefaultValidatorFactory().getValidator();

        assertThat(validador.validate(new AutorizacionRequest(null))).isNotEmpty();
        assertThat(validador.validate(new AutorizacionRequest(""))).isNotEmpty();
        assertThat(validador.validate(new AutorizacionRequest("   "))).isNotEmpty();
        assertThat(validador.validate(new AutorizacionRequest("x".repeat(501)))).isNotEmpty();
        assertThat(validador.validate(new AutorizacionRequest("Revisión en campo"))).isEmpty();
    }

    // ------------------------------------------------------------------ F08: consultar

    @Test
    void elAgronomoVeLaBandejaDePendientes() {
        when(repo.findByEstadoOrderByIdDesc(EstadoAplicacion.PENDIENTE_DE_VALIDACION))
                .thenReturn(List.of(pendiente(100L), pendiente(101L)));

        List<AplicacionResponse> lista = servicio.listar(EstadoAplicacion.PENDIENTE_DE_VALIDACION, agronomo);

        assertThat(lista).extracting(AplicacionResponse::id).containsExactly(100L, 101L);
    }

    @Test
    void sinFiltroDeEstadoElAgronomoVeTodas() {
        when(repo.findAllByOrderByIdDesc()).thenReturn(List.of(pendiente(100L)));

        assertThat(servicio.listar(null, agronomo)).hasSize(1);
    }

    @Test
    void elProductorVeSoloLasDeSusEstablecimientos() {
        when(repo.findByProductorYEstado("prod-1", EstadoAplicacion.PENDIENTE_DE_VALIDACION))
                .thenReturn(List.of(pendiente(100L)));

        assertThat(servicio.listar(EstadoAplicacion.PENDIENTE_DE_VALIDACION, productor)).hasSize(1);
        verify(repo, never()).findByEstadoOrderByIdDesc(any());
    }

    @Test
    void elOperarioNoPuedeListar() {
        assertThatThrownBy(() -> servicio.listar(null, operario)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void consultarUnaAplicacionMuestraSuEvaluacionDosisTotalYCosto() {
        Aplicacion a = pendiente(100L);
        a.setEstado(EstadoAplicacion.REGISTRADA);
        a.setEvaluacionId(55L);
        a.setDosisTotal(new BigDecimal("40.000"));
        a.setEnvases(4);
        a.setCosto(new BigDecimal("32000.00"));
        when(repo.findById(100L)).thenReturn(Optional.of(a));

        AplicacionResponse r = servicio.obtener(100L, operario);

        assertThat(r.evaluacionId()).isEqualTo(55L);
        assertThat(r.dosisTotal()).isEqualByComparingTo("40");
        assertThat(r.envases()).isEqualTo(4);
        assertThat(r.costo()).isEqualByComparingTo("32000");
    }

    @Test
    void consultarUnaAplicacionAjenaEstaProhibidoPeroElAgronomoVeTodas() {
        Aplicacion a = pendiente(100L);
        when(repo.findById(100L)).thenReturn(Optional.of(a));

        assertThatThrownBy(() -> servicio.obtener(100L, otroProductor)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> servicio.obtener(100L, operarioNoAsignado)).isInstanceOf(AccessDeniedException.class);
        assertThat(servicio.obtener(100L, agronomo).id()).isEqualTo(100L);
    }
}
