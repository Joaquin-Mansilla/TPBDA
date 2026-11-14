package ar.edu.agro.campos.service;

import ar.edu.agro.commons.error.RecursoNoEncontradoException;
import ar.edu.agro.campos.dto.CamposDtos.*;
import ar.edu.agro.campos.model.Establecimiento;
import ar.edu.agro.campos.repository.EstablecimientoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    /** F01: quien lo crea queda como dueño. */
    @Transactional
    public EstablecimientoResponse crear(EstablecimientoRequest r, Usuario u) {
        Establecimiento e = new Establecimiento();
        e.setNombre(r.nombre());
        e.setProductorSub(u.sub());
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
        return new EstablecimientoResponse(e.getId(), e.getNombre(), e.getProductorSub(), new TreeSet<>(e.getOperarios()),
                e.getLotes().stream().map(l -> new LoteResumen(l.getId(), l.getNombre(), l.getSuperficieHa(), l.getEstadoSuelo())).toList());
    }
}
