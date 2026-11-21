package ar.edu.agro.campos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.agro.campos.dto.CamposDtos.EstablecimientoRequest;
import ar.edu.agro.campos.dto.CamposDtos.EstablecimientoResponse;
import ar.edu.agro.campos.model.Establecimiento;
import ar.edu.agro.campos.repository.EstablecimientoRepository;
import ar.edu.agro.commons.error.SolicitudInvalidaException;
import ar.edu.agro.commons.security.Roles;
import java.math.BigDecimal;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** El establecimiento se define con un rectángulo de coordenadas y se calculan sus hectáreas (T16). */
class EstablecimientoServiceTest {

    private final Usuario productor = new Usuario("prod-1", Set.of(Roles.PRODUCTOR));
    private EstablecimientoRepository repo;
    private EstablecimientoService servicio;

    @BeforeEach
    void preparar() {
        repo = mock(EstablecimientoRepository.class);
        when(repo.save(any(Establecimiento.class))).thenAnswer(i -> i.getArgument(0));
        servicio = new EstablecimientoService(repo, new AccesoService());
    }

    private static EstablecimientoRequest pedido(String latMin, String latMax, String lonMin, String lonMax) {
        return new EstablecimientoRequest("Campo", Set.of(), new BigDecimal(latMin), new BigDecimal(latMax),
                new BigDecimal(lonMin), new BigDecimal(lonMax));
    }

    @Test
    void alCrearloSeCalculanSusHectareasYTodaLaSuperficieEstaLibre() {
        EstablecimientoResponse r = servicio.crear(pedido("-32.71", "-32.69", "-62.11", "-62.09"), productor);

        assertThat(r.superficieHa().doubleValue()).isBetween(410.0, 420.0);
        assertThat(r.superficieLibreHa()).isEqualByComparingTo(r.superficieHa());
        assertThat(r.latitudMin()).isEqualByComparingTo("-32.71");
        assertThat(r.productorSub()).isEqualTo("prod-1");
    }

    @Test
    void siLosLimitesNoFormanUnRectanguloEs400() {
        assertThatThrownBy(() -> servicio.crear(pedido("-32.69", "-32.71", "-62.11", "-62.09"), productor))
                .isInstanceOf(SolicitudInvalidaException.class);
        assertThatThrownBy(() -> servicio.crear(pedido("-32.71", "-32.69", "-62.09", "-62.09"), productor))
                .isInstanceOf(SolicitudInvalidaException.class);
        verify(repo, never()).save(any());
    }
}
