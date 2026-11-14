package ar.edu.agro.tratamientos.service;

import ar.edu.agro.commons.contract.CultivoResumenResponse;
import ar.edu.agro.commons.error.ReglaNegocioException;
import ar.edu.agro.commons.error.RecursoNoEncontradoException;
import ar.edu.agro.tratamientos.dto.*;
import ar.edu.agro.tratamientos.model.*;
import ar.edu.agro.tratamientos.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Administración del catálogo agronómico (F10) y su consulta (F12). Las bajas son lógicas (activo = false). */
@Service
public class CatalogoService {

    private final CultivoRepository cultivos;
    private final PlagaRepository plagas;
    private final AgroquimicoRepository agroquimicos;
    private final HabilitacionRepository habilitaciones;
    private final AjusteSueloRepository ajustes;
    private final RestriccionTerritorialRepository restricciones;

    public CatalogoService(CultivoRepository cultivos, PlagaRepository plagas, AgroquimicoRepository agroquimicos,
                           HabilitacionRepository habilitaciones, AjusteSueloRepository ajustes,
                           RestriccionTerritorialRepository restricciones) {
        this.cultivos = cultivos;
        this.plagas = plagas;
        this.agroquimicos = agroquimicos;
        this.habilitaciones = habilitaciones;
        this.ajustes = ajustes;
        this.restricciones = restricciones;
    }

    // ---------------- cultivos
    @Transactional(readOnly = true)
    public List<CultivoResumenResponse> listarCultivos() {
        return cultivos.findByActivoTrueOrderByNombre().stream().map(this::aRespuesta).toList();
    }

    /** Devuelve el cultivo aunque esté dado de baja: campañas existentes lo siguen referenciando. */
    @Transactional(readOnly = true)
    public CultivoResumenResponse obtenerCultivo(Long id) {
        return aRespuesta(cultivo(id));
    }

    @Transactional
    public CultivoResumenResponse crearCultivo(CultivoRequest r) {
        if (cultivos.existsByNombreIgnoreCase(r.nombre())) {
            throw ErroresDeRegla.de("CAT", "Ya existe un cultivo llamado " + r.nombre());
        }
        Cultivo c = new Cultivo();
        copiar(r, c);
        return aRespuesta(cultivos.save(c));
    }

    @Transactional
    public CultivoResumenResponse modificarCultivo(Long id, CultivoRequest r) {
        Cultivo c = cultivo(id);
        if (cultivos.existsByNombreIgnoreCaseAndIdNot(r.nombre(), id)) {
            throw ErroresDeRegla.de("CAT", "Ya existe un cultivo llamado " + r.nombre());
        }
        copiar(r, c);
        return aRespuesta(c);
    }

    @Transactional
    public void bajaCultivo(Long id) {
        cultivo(id).setActivo(false);
    }

    private void copiar(CultivoRequest r, Cultivo c) {
        c.setNombre(r.nombre());
        c.setFamilia(r.familia());
        c.setDiasCiclo(r.diasCiclo());
        c.setMesSiembraDesde(r.mesSiembraDesde());
        c.setMesSiembraHasta(r.mesSiembraHasta());
    }

    private Cultivo cultivo(Long id) {
        return cultivos.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("No existe el cultivo " + id));
    }

    private CultivoResumenResponse aRespuesta(Cultivo c) {
        return new CultivoResumenResponse(c.getId(), c.getNombre(), c.getFamilia(), c.getDiasCiclo(),
                c.getMesSiembraDesde(), c.getMesSiembraHasta());
    }

    // ---------------- plagas
    @Transactional(readOnly = true)
    public List<PlagaResponse> listarPlagas() {
        return plagas.findByActivoTrueOrderByNombre().stream().map(p -> new PlagaResponse(p.getId(), p.getNombre())).toList();
    }

    @Transactional
    public PlagaResponse crearPlaga(PlagaRequest r) {
        if (plagas.existsByNombreIgnoreCase(r.nombre())) {
            throw ErroresDeRegla.de("CAT", "Ya existe una plaga llamada " + r.nombre());
        }
        Plaga p = new Plaga();
        p.setNombre(r.nombre());
        plagas.save(p);
        return new PlagaResponse(p.getId(), p.getNombre());
    }

    @Transactional
    public PlagaResponse modificarPlaga(Long id, PlagaRequest r) {
        Plaga p = plaga(id);
        if (plagas.existsByNombreIgnoreCaseAndIdNot(r.nombre(), id)) {
            throw ErroresDeRegla.de("CAT", "Ya existe una plaga llamada " + r.nombre());
        }
        p.setNombre(r.nombre());
        return new PlagaResponse(p.getId(), p.getNombre());
    }

    @Transactional
    public void bajaPlaga(Long id) {
        plaga(id).setActivo(false);
    }

    private Plaga plaga(Long id) {
        return plagas.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("No existe la plaga " + id));
    }

    // ---------------- agroquímicos
    @Transactional(readOnly = true)
    public List<AgroquimicoResponse> listarAgroquimicos() {
        return agroquimicos.findByActivoTrueOrderByNombre().stream().map(this::aRespuesta).toList();
    }

    @Transactional(readOnly = true)
    public AgroquimicoResponse obtenerAgroquimico(Long id) {
        return aRespuesta(agroquimico(id));
    }

    @Transactional
    public AgroquimicoResponse crearAgroquimico(AgroquimicoRequest r) {
        if (agroquimicos.existsByNombreIgnoreCase(r.nombre())) {
            throw ErroresDeRegla.de("CAT", "Ya existe un agroquímico llamado " + r.nombre());
        }
        Agroquimico a = new Agroquimico();
        copiar(r, a);
        agroquimicos.save(a);
        guardarRelaciones(r, a);
        return aRespuesta(a);
    }

    @Transactional
    public AgroquimicoResponse modificarAgroquimico(Long id, AgroquimicoRequest r) {
        Agroquimico a = agroquimico(id);
        if (agroquimicos.existsByNombreIgnoreCaseAndIdNot(r.nombre(), id)) {
            throw ErroresDeRegla.de("CAT", "Ya existe un agroquímico llamado " + r.nombre());
        }
        copiar(r, a);
        // se reemplazan las relaciones; el flush evita choques con las restricciones de unicidad
        habilitaciones.deleteByAgroquimicoId(id);
        ajustes.deleteByAgroquimicoId(id);
        restricciones.deleteByAgroquimicoId(id);
        habilitaciones.flush();
        ajustes.flush();
        restricciones.flush();
        guardarRelaciones(r, a);
        return aRespuesta(a);
    }

    @Transactional
    public void bajaAgroquimico(Long id) {
        agroquimico(id).setActivo(false);
    }

    private void copiar(AgroquimicoRequest r, Agroquimico a) {
        a.setNombre(r.nombre());
        a.setPrincipioActivo(r.principioActivo());
        a.setDosisMinHa(r.dosisMinHa());
        a.setDosisMaxHa(r.dosisMaxHa());
        a.setUnidad(r.unidad());
        a.setContenidoEnvase(r.contenidoEnvase());
        a.setPrecioEnvase(r.precioEnvase());
        a.setDiasCarencia(r.diasCarencia());
        a.setIntervaloMinDias(r.intervaloMinDias());
    }

    private void guardarRelaciones(AgroquimicoRequest r, Agroquimico a) {
        if (r.habilitaciones() != null) {
            for (var h : r.habilitaciones()) {
                Cultivo c = cultivos.findById(h.cultivoId())
                        .orElseThrow(() -> new RecursoNoEncontradoException("No existe el cultivo " + h.cultivoId()));
                Plaga p = plaga(h.plagaId());
                Habilitacion hab = new Habilitacion();
                hab.setAgroquimico(a);
                hab.setCultivo(c);
                hab.setPlaga(p);
                habilitaciones.save(hab);
            }
        }
        if (r.ajustesSuelo() != null) {
            for (var aj : r.ajustesSuelo()) {
                AjusteSuelo e = new AjusteSuelo();
                e.setAgroquimico(a);
                e.setClaseTextura(aj.claseTextura());
                e.setFactorDosisMax(aj.factorDosisMax());
                ajustes.save(e);
            }
        }
        if (r.restricciones() != null) {
            for (var rs : r.restricciones()) {
                RestriccionTerritorial e = new RestriccionTerritorial();
                e.setAgroquimico(a);
                e.setProvincia(rs.provincia());
                e.setDepartamento(rs.departamento() == null || rs.departamento().isBlank() ? null : rs.departamento());
                restricciones.save(e);
            }
        }
    }

    private Agroquimico agroquimico(Long id) {
        return agroquimicos.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("No existe el agroquímico " + id));
    }

    private AgroquimicoResponse aRespuesta(Agroquimico a) {
        return new AgroquimicoResponse(a.getId(), a.getNombre(), a.getPrincipioActivo(), a.getDosisMinHa(),
                a.getDosisMaxHa(), a.getUnidad(), a.getContenidoEnvase(), a.getPrecioEnvase(), a.getDiasCarencia(),
                a.getIntervaloMinDias(), a.isActivo(),
                habilitaciones.findByAgroquimicoId(a.getId()).stream()
                        .map(h -> new AgroquimicoRequest.HabilitacionDto(h.getCultivo().getId(), h.getPlaga().getId())).toList(),
                ajustes.findByAgroquimicoId(a.getId()).stream()
                        .map(x -> new AgroquimicoRequest.AjusteSueloDto(x.getClaseTextura(), x.getFactorDosisMax())).toList(),
                restricciones.findByAgroquimicoId(a.getId()).stream()
                        .map(x -> new AgroquimicoRequest.RestriccionDto(x.getProvincia(), x.getDepartamento())).toList());
    }
}
