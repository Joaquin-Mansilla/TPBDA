package ar.edu.agro.campos.service;

import ar.edu.agro.commons.error.RecursoNoEncontradoException;
import ar.edu.agro.commons.error.SolicitudInvalidaException;
import ar.edu.agro.campos.dto.CamposDtos.*;
import ar.edu.agro.campos.model.Establecimiento;
import ar.edu.agro.campos.model.GeometriaCampo;
import ar.edu.agro.campos.model.Lote;
import ar.edu.agro.campos.repository.EstablecimientoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.TreeSet;

@Service
public class EstablecimientoService {

    private final EstablecimientoRepository establecimientos;
    private final AccesoService acceso;

    public EstablecimientoService(EstablecimientoRepository establecimientos, AccesoService acceso) {
        this.establecimientos = establecimientos;
        this.acceso = acceso;
    }

    /** F01: quien lo crea queda como dueño. Sus hectáreas salen del rectángulo de coordenadas (T16). */
    @Transactional
    public EstablecimientoResponse crear(EstablecimientoRequest r, Usuario u) {
        if (r.latitudMin().compareTo(r.latitudMax()) >= 0 || r.longitudMin().compareTo(r.longitudMax()) >= 0) {
            throw new SolicitudInvalidaException("Los límites del establecimiento no forman un rectángulo: "
                    + "latitudMin tiene que ser menor que latitudMax y longitudMin menor que longitudMax");
        }
        Establecimiento e = new Establecimiento();
        e.setNombre(r.nombre());
        e.setProductorSub(u.sub());
        e.setProductorNombre(u.nombre());
        e.setLatitudMin(r.latitudMin());
        e.setLatitudMax(r.latitudMax());
        e.setLongitudMin(r.longitudMin());
        e.setLongitudMax(r.longitudMax());
        e.setSuperficieHa(GeometriaCampo.superficieHa(e.rectangulo()));
        if (r.operarios() != null) {
            e.setOperarios(new HashSet<>(r.operarios()));
        }
        establecimientos.save(e);
        return aRespuesta(e);
    }

    /** Productor: los propios. Operario: los asignados. Agrónomo: todos. */
    @Transactional(readOnly = true)
    public List<EstablecimientoResponse> listar(Usuario u) {
        List<Establecimiento> lista;
        if (u.esAgronomo()) {
            lista = establecimientos.findAll();
        } else if (u.esProductor()) {
            lista = establecimientos.findByProductorSubOrderById(u.sub());
        } else {
            lista = establecimientos.findByOperario(u.sub());
        }
        return lista.stream().map(this::aRespuesta).toList();
    }

    @Transactional(readOnly = true)
    public EstablecimientoResponse obtener(Long id, Usuario u) {
        Establecimiento e = buscar(id);
        acceso.exigirLectura(e, u);
        return aRespuesta(e);
    }

    public Establecimiento buscar(Long id) {
        return establecimientos.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el establecimiento " + id));
    }

    private EstablecimientoResponse aRespuesta(Establecimiento e) {
        BigDecimal ocupada = e.getLotes().stream().map(Lote::getSuperficieHa).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal libre = e.getSuperficieHa() == null ? null : e.getSuperficieHa().subtract(ocupada);
        return new EstablecimientoResponse(e.getId(), e.getNombre(), e.getProductorSub(), e.getProductorNombre(), new TreeSet<>(e.getOperarios()),
                e.getLotes().stream().map(l -> new LoteResumen(l.getId(), l.getNombre(), l.getSuperficieHa(), l.getEstadoSuelo())).toList(),
                e.getLatitudMin(), e.getLatitudMax(), e.getLongitudMin(), e.getLongitudMax(), e.getSuperficieHa(), libre);
    }
}
