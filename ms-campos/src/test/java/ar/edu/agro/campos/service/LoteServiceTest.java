package ar.edu.agro.campos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ar.edu.agro.campos.client.ClasificadorTextura;
import ar.edu.agro.campos.client.DatosExternosLote;
import ar.edu.agro.campos.client.DatosExternosService;
import ar.edu.agro.campos.config.ApisExternasProperties;
import ar.edu.agro.campos.dto.CamposDtos.LoteRequest;
import ar.edu.agro.campos.dto.CamposDtos.LoteResponse;
import ar.edu.agro.campos.dto.CamposDtos.SueloRequest;
import ar.edu.agro.campos.model.Establecimiento;
import ar.edu.agro.campos.model.EstadoSuelo;
import ar.edu.agro.campos.model.Lote;
import ar.edu.agro.campos.repository.LoteRepository;
import ar.edu.agro.commons.contract.ClaseTextura;
import ar.edu.agro.commons.contract.SueloDto;
import ar.edu.agro.commons.contract.UbicacionDto;
import ar.edu.agro.commons.error.ServicioNoDisponibleException;
import ar.edu.agro.commons.error.SolicitudInvalidaException;
import ar.edu.agro.commons.security.Roles;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

/** Creación de lotes con las APIs externas (T09 integrada) y carga manual del suelo. */
class LoteServiceTest {

    private static final BigDecimal LAT = new BigDecimal("-31.42");
    private static final BigDecimal LON = new BigDecimal("-64.18");

    private final Usuario productor = new Usuario("prod-1", Set.of(Roles.PRODUCTOR));
    private final Usuario otroProductor = new Usuario("prod-2", Set.of(Roles.PRODUCTOR));

    private LoteRepository lotes;
    private DatosExternosService externos;
    private LoteService servicio;
    private Lote existente;

    @BeforeEach
    void preparar() {
        lotes = mock(LoteRepository.class);
        externos = mock(DatosExternosService.class);
        EstablecimientoService establecimientos = mock(EstablecimientoService.class);

        Establecimiento e = new Establecimiento();
        e.setId(1L);
        e.setProductorSub("prod-1");
        when(establecimientos.buscar(1L)).thenReturn(e);

        ApisExternasProperties props = new ApisExternasProperties(true,
                new ApisExternasProperties.SoilGrids("http://x", "0-5cm", Duration.ofSeconds(1), Duration.ofSeconds(1), 0),
                new ApisExternasProperties.Georef("http://y", Duration.ofSeconds(1), Duration.ofSeconds(1), 0),
                new ApisExternasProperties.Textura(new BigDecimal("50"), new BigDecimal("35")));
        servicio = new LoteService(lotes, establecimientos, new AccesoService(), externos, new ClasificadorTextura(props));

        existente = new Lote();
        existente.setId(7L);
        existente.setEstablecimiento(e);
        existente.setLatitud(LAT);
        existente.setLongitud(LON);
        existente.setSuperficieHa(new BigDecimal("50"));
        existente.setEstadoSuelo(EstadoSuelo.SIN_DATOS);
        when(lotes.findById(7L)).thenReturn(Optional.of(existente));
        when(lotes.save(any(Lote.class))).thenAnswer(inv -> {
            Lote l = inv.getArgument(0);
            if (l.getId() == null) {
                l.setId(9L);
            }
            return l;
        });
    }

    private LoteRequest pedido() {
        return new LoteRequest("Lote Norte", LAT, LON, new BigDecimal("100"));
    }

    private SueloDto suelo() {
        return new SueloDto(ClaseTextura.FRANCO, new BigDecimal("23.5"), new BigDecimal("41.2"),
                new BigDecimal("35.3"), new BigDecimal("6.2"), new BigDecimal("14.8"));
    }

    @Test
    void siRespondenLasDosApisElLoteQuedaConSueloYUbicacion() {
        when(externos.consultar(LAT, LON)).thenReturn(
                new DatosExternosLote(suelo(), new UbicacionDto("Córdoba", "Capital", "Córdoba")));

        LoteResponse r = servicio.crear(1L, pedido(), productor);

        assertThat(r.estadoSuelo()).isEqualTo(EstadoSuelo.OK);
        assertThat(r.suelo().claseTextura()).isEqualTo(ClaseTextura.FRANCO);
        assertThat(r.suelo().carbonoOrganicoGKg()).isEqualByComparingTo("14.8");
        assertThat(r.provincia()).isEqualTo("Córdoba");
        assertThat(r.departamento()).isEqualTo("Capital");
    }

    @Test
    void siFallanLasDosApisElLoteSeCreaIgualConSueloSinDatos() {
        when(externos.consultar(LAT, LON)).thenReturn(new DatosExternosLote(null, null));

        LoteResponse r = servicio.crear(1L, pedido(), productor);

        assertThat(r.id()).isEqualTo(9L);
        assertThat(r.estadoSuelo()).isEqualTo(EstadoSuelo.SIN_DATOS);
        assertThat(r.suelo()).isNull();
        assertThat(r.provincia()).isNull();
    }

    @Test
    void siSoloFallaSoilGridsElLoteTieneUbicacionPeroSueloSinDatos() {
        when(externos.consultar(LAT, LON)).thenReturn(
                new DatosExternosLote(null, new UbicacionDto("Córdoba", "Capital", null)));

        LoteResponse r = servicio.crear(1L, pedido(), productor);

        assertThat(r.estadoSuelo()).isEqualTo(EstadoSuelo.SIN_DATOS);
        assertThat(r.provincia()).isEqualTo("Córdoba");
    }

    @Test
    void soloElProductorDuenioCreaLotes() {
        assertThatThrownBy(() -> servicio.crear(1L, pedido(), otroProductor)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void refrescarSinRespuestaDeNingunaApiEs503YElLoteNoCambia() {
        when(externos.consultar(LAT, LON)).thenReturn(new DatosExternosLote(null, null));

        assertThatThrownBy(() -> servicio.refrescar(7L)).isInstanceOf(ServicioNoDisponibleException.class);
        assertThat(existente.getEstadoSuelo()).isEqualTo(EstadoSuelo.SIN_DATOS);
    }

    @Test
    void refrescarCompletaElSueloDeUnLoteSinDatos() {
        when(externos.consultar(LAT, LON)).thenReturn(new DatosExternosLote(suelo(), null));

        LoteResponse r = servicio.refrescar(7L);

        assertThat(r.estadoSuelo()).isEqualTo(EstadoSuelo.OK);
    }

    @Test
    void laCargaManualClasificaConLaReglaDeLaPropuestaArenaDesde50() {
        SueloRequest manual = new SueloRequest(null, new BigDecimal("20"), new BigDecimal("55"), new BigDecimal("25"),
                new BigDecimal("6.5"), new BigDecimal("12"), "Córdoba", "Río Cuarto");

        LoteResponse r = servicio.actualizarSuelo(7L, manual);

        assertThat(r.suelo().claseTextura()).isEqualTo(ClaseTextura.ARENOSO);
        assertThat(r.estadoSuelo()).isEqualTo(EstadoSuelo.OK);
        assertThat(r.provincia()).isEqualTo("Córdoba");
        assertThat(r.departamento()).isEqualTo("Río Cuarto");
    }

    @Test
    void laCargaManualPuedeIndicarLaClaseDeTexturaDirectamente() {
        SueloRequest manual = new SueloRequest(ClaseTextura.ARCILLOSO, null, null, null, null, null, null, null);

        assertThat(servicio.actualizarSuelo(7L, manual).suelo().claseTextura()).isEqualTo(ClaseTextura.ARCILLOSO);
    }

    @Test
    void laCargaManualSinClaseNiPorcentajesEs400() {
        SueloRequest vacio = new SueloRequest(null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> servicio.actualizarSuelo(7L, vacio)).isInstanceOf(SolicitudInvalidaException.class);
    }
}
