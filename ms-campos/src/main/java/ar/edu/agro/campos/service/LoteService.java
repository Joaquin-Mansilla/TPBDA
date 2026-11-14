package ar.edu.agro.campos.service;

import ar.edu.agro.campos.client.ClasificadorTextura;
import ar.edu.agro.campos.client.DatosExternosLote;
import ar.edu.agro.campos.client.DatosExternosService;
import ar.edu.agro.campos.dto.CamposDtos.LoteRequest;
import ar.edu.agro.campos.dto.CamposDtos.LoteResponse;
import ar.edu.agro.campos.dto.CamposDtos.SueloRequest;
import ar.edu.agro.campos.model.Establecimiento;
import ar.edu.agro.campos.model.EstadoSuelo;
import ar.edu.agro.campos.model.Lote;
import ar.edu.agro.campos.repository.LoteRepository;
import ar.edu.agro.commons.contract.ClaseTextura;
import ar.edu.agro.commons.contract.SueloDto;
import ar.edu.agro.commons.error.RecursoNoEncontradoException;
import ar.edu.agro.commons.error.ServicioNoDisponibleException;
import ar.edu.agro.commons.error.SolicitudInvalidaException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lotes (F02, F03). El suelo y la ubicación se consultan una sola vez a las APIs externas y se guardan (L1). */
@Service
public class LoteService {

    private final LoteRepository lotes;
    private final EstablecimientoService establecimientos;
    private final AccesoService acceso;
    private final DatosExternosService externos;
    private final ClasificadorTextura clasificador;

    public LoteService(LoteRepository lotes, EstablecimientoService establecimientos, AccesoService acceso,
                       DatosExternosService externos, ClasificadorTextura clasificador) {
        this.lotes = lotes;
        this.establecimientos = establecimientos;
        this.acceso = acceso;
        this.externos = externos;
        this.clasificador = clasificador;
    }

    /** F02. Si SoilGrids o Georef fallan, el lote se crea igual: SIN_DATOS / sin ubicación (L1). */
    @Transactional
    public LoteResponse crear(Long establecimientoId, LoteRequest r, Usuario u) {
        Establecimiento e = establecimientos.buscar(establecimientoId);
        acceso.exigirDueno(e, u);
        Lote l = new Lote();
        l.setEstablecimiento(e);
        l.setNombre(r.nombre());
        l.setLatitud(r.latitud());
        l.setLongitud(r.longitud());
        l.setSuperficieHa(r.superficieHa());
        l.setEstadoSuelo(EstadoSuelo.SIN_DATOS);
        completarDatosExternos(l);
        lotes.save(l);
        return aRespuesta(l);
    }

    @Transactional(readOnly = true)
    public LoteResponse obtener(Long id, Usuario u) {
        Lote l = buscar(id);
        acceso.exigirLectura(l.getEstablecimiento(), u);
        return aRespuesta(l);
    }

    /** F03: el agrónomo carga o corrige a mano el suelo (y, si hace falta, la ubicación). */
    @Transactional
    public LoteResponse actualizarSuelo(Long id, SueloRequest r) {
        Lote l = buscar(id);
        ClaseTextura clase = r.claseTextura();
        if (clase == null && r.arcillaPct() != null && r.arenaPct() != null) {
            clase = clasificador.clasificar(r.arenaPct(), r.arcillaPct());
        }
        if (clase == null) {
            throw new SolicitudInvalidaException("Hay que indicar la clase de textura o los porcentajes de arcilla y arena");
        }
        l.setClaseTextura(clase);
        l.setArcillaPct(r.arcillaPct());
        l.setArenaPct(r.arenaPct());
        l.setLimoPct(r.limoPct());
        l.setPh(r.ph());
        l.setCarbonoOrganico(r.carbonoOrganico());
        l.setEstadoSuelo(EstadoSuelo.OK);
        if (r.provincia() != null && !r.provincia().isBlank()) {
            l.setProvincia(r.provincia());
            l.setDepartamento(r.departamento());
        }
        return aRespuesta(l);
    }

    /** F03: vuelve a consultar SoilGrids y Georef. Si ninguna responde, 503 y el lote queda como estaba. */
    @Transactional
    public LoteResponse refrescar(Long id) {
        Lote l = buscar(id);
        boolean algo = completarDatosExternos(l);
        if (!algo) {
            throw new ServicioNoDisponibleException("SoilGrids y Georef no respondieron: el lote queda como estaba");
        }
        return aRespuesta(l);
    }

    public Lote buscar(Long id) {
        return lotes.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("No existe el lote " + id));
    }

    /** @return true si al menos una de las dos APIs devolvió datos */
    private boolean completarDatosExternos(Lote l) {
        DatosExternosLote datos = externos.consultar(l.getLatitud(), l.getLongitud());

        if (datos.sueloObtenido()) {
            SueloDto s = datos.suelo();
            l.setClaseTextura(s.claseTextura());
            l.setArcillaPct(s.arcillaPct());
            l.setArenaPct(s.arenaPct());
            l.setLimoPct(s.limoPct());
            l.setPh(s.ph());
            l.setCarbonoOrganico(s.carbonoOrganicoGKg());
            l.setEstadoSuelo(EstadoSuelo.OK);
        }
        if (datos.ubicacionObtenida()) {
            l.setProvincia(datos.ubicacion().provincia());
            l.setDepartamento(datos.ubicacion().departamento());
        }
        return datos.sueloObtenido() || datos.ubicacionObtenida();
    }

    private LoteResponse aRespuesta(Lote l) {
        SueloDto suelo = l.getEstadoSuelo() == EstadoSuelo.OK
                ? new SueloDto(l.getClaseTextura(), l.getArcillaPct(), l.getArenaPct(), l.getLimoPct(), l.getPh(), l.getCarbonoOrganico())
                : null;
        return new LoteResponse(l.getId(), l.getEstablecimiento().getId(), l.getNombre(), l.getLatitud(), l.getLongitud(),
                l.getSuperficieHa(), l.getProvincia(), l.getDepartamento(), l.getEstadoSuelo(), suelo);
    }
}
