package ar.edu.agro.campos.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ar.edu.agro.campos.config.ApisExternasProperties;
import ar.edu.agro.commons.contract.ClaseTextura;
import ar.edu.agro.commons.contract.SueloDto;
import ar.edu.agro.commons.contract.UbicacionDto;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DatosExternosServiceTest {

    private static final BigDecimal LAT = new BigDecimal("-31.42");
    private static final BigDecimal LON = new BigDecimal("-64.18");

    private final SueloDto suelo = new SueloDto(ClaseTextura.FRANCO, new BigDecimal("23.5"),
            new BigDecimal("41.2"), new BigDecimal("35.3"), new BigDecimal("6.2"), new BigDecimal("14.8"));
    private final UbicacionDto ubicacion = new UbicacionDto("Córdoba", "Capital", "Córdoba");

    private SoilGridsClient soilGrids;
    private GeorefClient georef;
    private DatosExternosService servicio;

    @BeforeEach
    void preparar() {
        soilGrids = mock(SoilGridsClient.class);
        georef = mock(GeorefClient.class);
        servicio = new DatosExternosService(soilGrids, georef, PropiedadesDePrueba.con(1));
    }

    @Test
    void siRespondenLasDosDevuelveSueloYUbicacion() {
        when(soilGrids.consultarSuelo(LAT, LON)).thenReturn(suelo);
        when(georef.consultarUbicacion(LAT, LON)).thenReturn(ubicacion);

        DatosExternosLote datos = servicio.consultar(LAT, LON);

        assertThat(datos.sueloObtenido()).isTrue();
        assertThat(datos.ubicacionObtenida()).isTrue();
        assertThat(datos.suelo()).isEqualTo(suelo);
        assertThat(datos.ubicacion()).isEqualTo(ubicacion);
    }

    @Test
    void siFallaSoilGridsElSueloQuedaEnNullYLaUbicacionSeConserva() {
        when(soilGrids.consultarSuelo(LAT, LON)).thenThrow(new ApiExternaException("SoilGrids", "sin respuesta"));
        when(georef.consultarUbicacion(LAT, LON)).thenReturn(ubicacion);

        DatosExternosLote datos = servicio.consultar(LAT, LON);

        assertThat(datos.sueloObtenido()).isFalse();
        assertThat(datos.suelo()).isNull();
        assertThat(datos.ubicacion()).isEqualTo(ubicacion);
    }

    @Test
    void siFallaGeorefLaUbicacionQuedaEnNullYElSueloSeConserva() {
        when(soilGrids.consultarSuelo(LAT, LON)).thenReturn(suelo);
        when(georef.consultarUbicacion(LAT, LON)).thenThrow(new ApiExternaException("Georef", "sin respuesta"));

        DatosExternosLote datos = servicio.consultar(LAT, LON);

        assertThat(datos.suelo()).isEqualTo(suelo);
        assertThat(datos.ubicacionObtenida()).isFalse();
    }

    @Test
    void siFallanLasDosElLoteSigueSiendoPosible() {
        when(soilGrids.consultarSuelo(LAT, LON)).thenThrow(new ApiExternaException("SoilGrids", "x"));
        when(georef.consultarUbicacion(LAT, LON)).thenThrow(new ApiExternaException("Georef", "x"));

        DatosExternosLote datos = servicio.consultar(LAT, LON);

        assertThat(datos.sueloObtenido()).isFalse();
        assertThat(datos.ubicacionObtenida()).isFalse();
    }

    @Test
    void laConsultaEstrictaDeSueloPropagaLaFalla() {
        when(soilGrids.consultarSuelo(LAT, LON)).thenThrow(new ApiExternaException("SoilGrids", "sin respuesta"));

        assertThatThrownBy(() -> servicio.consultarSuelo(LAT, LON))
                .isInstanceOf(ApiExternaException.class);
    }

    @Test
    void conLasConsultasDeshabilitadasNoLlamaANingunaApi() {
        ApisExternasProperties props = PropiedadesDePrueba.con(1);
        ApisExternasProperties apagado = new ApisExternasProperties(false, props.soilgrids(), props.georef(), props.textura());
        DatosExternosService sinApis = new DatosExternosService(soilGrids, georef, apagado);

        DatosExternosLote datos = sinApis.consultar(LAT, LON);

        assertThat(datos.sueloObtenido()).isFalse();
        assertThat(datos.ubicacionObtenida()).isFalse();
        verifyNoInteractions(soilGrids, georef);
        assertThatThrownBy(() -> sinApis.consultarSuelo(LAT, LON)).isInstanceOf(ApiExternaException.class);
        assertThatThrownBy(() -> sinApis.consultarUbicacion(LAT, LON)).isInstanceOf(ApiExternaException.class);
    }
}
