package ar.edu.agro.campos.service;

import ar.edu.agro.campos.client.TratamientosClient;
import ar.edu.agro.campos.dto.CamposDtos.AplicacionRequest;
import ar.edu.agro.campos.dto.CamposDtos.AplicacionResponse;
import ar.edu.agro.campos.dto.CamposDtos.AplicacionResultadoResponse;
import ar.edu.agro.campos.dto.CamposDtos.AutorizacionRequest;
import ar.edu.agro.campos.model.Aplicacion;
import ar.edu.agro.campos.model.Campania;
import ar.edu.agro.campos.model.EstadoAplicacion;
import ar.edu.agro.campos.model.EstadoCampania;
import ar.edu.agro.campos.model.EstadoSuelo;
import ar.edu.agro.campos.model.Lote;
import ar.edu.agro.campos.repository.AplicacionRepository;
import ar.edu.agro.commons.contract.AplicacionPreviaDto;
import ar.edu.agro.commons.contract.EvaluacionRequest;
import ar.edu.agro.commons.contract.EvaluacionResponse;
import ar.edu.agro.commons.contract.MotivoDto;
import ar.edu.agro.commons.contract.ResultadoEvaluacion;
import ar.edu.agro.commons.contract.SueloDto;
import ar.edu.agro.commons.contract.UbicacionDto;
import ar.edu.agro.commons.error.RecursoNoEncontradoException;
import ar.edu.agro.commons.error.ServicioNoDisponibleException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/**
 * Proceso principal (F07 a F09): ms-campos valida pertenencia, estado de la campaña y superficie, y pide la
 * evaluación a ms-tratamientos con el suelo, la ubicación y el historial del lote.
 *
 * Registrar (F07):
 *   VIABLE                       -> REGISTRADA (201)
 *   REPROGRAMAR / NO_RECOMENDADO -> no se guarda (422 con la fecha sugerida o los motivos)
 *   REVISION_MANUAL              -> PENDIENTE_DE_VALIDACION (202), faltan datos del lote (L1)
 *   ms-tratamientos no responde  -> PENDIENTE_DE_VALIDACION (202): nunca se aprueba sola
 *   cultivo, plaga o agroquímico inexistente -> 404, no se guarda
 *
 * Resolver una pendiente (F09), solo el AGRONOMO:
 *   reevaluar: vuelve a consultar a ms-tratamientos; solo pasa a REGISTRADA si ahora da VIABLE
 *   autorizar: la pasa a AUTORIZADA_MANUALMENTE con una justificación obligatoria (regla A1)
 *
 * Ningún método es @Transactional a propósito: no se mantiene una transacción abierta mientras se espera a otro
 * servicio. Si dos agrónomos resuelven la misma pendiente a la vez, la columna version hace que el segundo reciba 409.
 */
@Service
public class AplicacionService {

    private static final Logger log = LoggerFactory.getLogger(AplicacionService.class);

    public record ResultadoRegistro(HttpStatus status, AplicacionResultadoResponse cuerpo) {
    }

    private final AplicacionRepository aplicaciones;
    private final CampaniaService campanias;
    private final AccesoService acceso;
    private final TratamientosClient tratamientos;

    public AplicacionService(AplicacionRepository aplicaciones, CampaniaService campanias, AccesoService acceso,
                             TratamientosClient tratamientos) {
        this.aplicaciones = aplicaciones;
        this.campanias = campanias;
        this.acceso = acceso;
        this.tratamientos = tratamientos;
    }

    // ------------------------------------------------------------------ F07: registrar

    public ResultadoRegistro registrar(Long campaniaId, AplicacionRequest r, Usuario u) {
        Campania c = campanias.buscar(campaniaId);
        acceso.exigirDuenoOAsignado(c.getLote().getEstablecimiento(), u);   // C6

        List<MotivoDto> motivos = new ArrayList<>();
        if (c.getEstado() != EstadoCampania.SEMBRADA) {
            motivos.add(new MotivoDto("C5", "Solo se registran aplicaciones en campañas sembradas (estado actual: "
                    + c.getEstado() + ")"));
        }
        if (!ReglasCampania.c2SuperficieValida(r.superficieTratadaHa(), c.getSuperficieSembradaHa())) {
            motivos.add(new MotivoDto("C2", "La superficie tratada (" + r.superficieTratadaHa()
                    + " ha) supera la sembrada (" + c.getSuperficieSembradaHa() + " ha)"));
        }
        if (!motivos.isEmpty()) {
            throw ErroresDeRegla.de("La aplicación no cumple las reglas de la campaña", motivos);
        }

        Aplicacion a = new Aplicacion();
        a.setCampania(c);
        a.setAgroquimicoId(r.agroquimicoId());
        a.setPlagaId(r.plagaId());
        a.setDosisPorHa(r.dosisPorHa());
        a.setSuperficieTratadaHa(r.superficieTratadaHa());
        a.setFecha(r.fecha());
        a.setRegistradaPorSub(u.sub());

        EvaluacionResponse ev;
        try {
            ev = tratamientos.evaluar(armarPedido(c, a));
        } catch (ServicioNoDisponibleException e) {
            // No hay decisión: no se aprueba ni se rechaza. Queda para que la resuelva un agrónomo.
            a.setEstado(EstadoAplicacion.PENDIENTE_DE_VALIDACION);
            aplicaciones.save(a);
            log.warn("Aplicación {} pendiente de validación: ms-tratamientos no respondió ({})", a.getId(), e.getMessage());
            return new ResultadoRegistro(HttpStatus.ACCEPTED, new AplicacionResultadoResponse(a.getId(), a.getEstado(),
                    null, null, null, null, null, null, List.of(),
                    "ms-tratamientos no respondió: la aplicación queda pendiente de validación de un agrónomo"));
        }

        ResultadoEvaluacion resultado = ev.resultado();
        if (resultado == ResultadoEvaluacion.VIABLE) {
            a.setEstado(EstadoAplicacion.REGISTRADA);
            copiarEvaluacion(a, ev);
            aplicaciones.save(a);
            return new ResultadoRegistro(HttpStatus.CREATED, cuerpo(a, ev, "Aplicación registrada"));
        }
        if (resultado == ResultadoEvaluacion.REVISION_MANUAL) {
            a.setEstado(EstadoAplicacion.PENDIENTE_DE_VALIDACION);
            copiarEvaluacion(a, ev);
            aplicaciones.save(a);
            return new ResultadoRegistro(HttpStatus.ACCEPTED,
                    cuerpo(a, ev, "Faltan datos del lote: queda pendiente de validación de un agrónomo"));
        }
        // REPROGRAMAR o NO_RECOMENDADO: no se guarda
        String mensaje = resultado == ResultadoEvaluacion.REPROGRAMAR
                ? "Hay que reprogramar la aplicación (fecha sugerida: " + ev.fechaSugerida() + ")"
                : "La aplicación no es recomendable";
        return new ResultadoRegistro(HttpStatus.UNPROCESSABLE_ENTITY, cuerpo(null, ev, mensaje));
    }

    // ------------------------------------------------------------------ F09: resolver una pendiente

    /**
     * El agrónomo pide volver a evaluar una pendiente (por ejemplo, cuando ms-tratamientos vuelve a estar disponible
     * o cuando se completaron los datos del lote). La aplicación solo pasa a REGISTRADA si ahora da VIABLE; en
     * cualquier otro caso sigue pendiente y se guarda la referencia a la última evaluación.
     *   200 pasó a REGISTRADA | 202 sigue sin datos | 422 la evaluación no la recomienda (se puede autorizar a mano)
     *   503 ms-tratamientos no responde: la aplicación no cambia
     */
    public ResultadoRegistro reevaluar(Long id) {
        Aplicacion a = buscar(id);
        exigirPendiente(a);

        EvaluacionResponse ev = tratamientos.evaluar(armarPedido(a.getCampania(), a));

        copiarEvaluacion(a, ev);
        ResultadoEvaluacion resultado = ev.resultado();
        if (resultado == ResultadoEvaluacion.VIABLE) {
            a.setEstado(EstadoAplicacion.REGISTRADA);
            aplicaciones.save(a);
            return new ResultadoRegistro(HttpStatus.OK, cuerpo(a, ev, "Aplicación registrada tras la reevaluación"));
        }
        aplicaciones.save(a);
        if (resultado == ResultadoEvaluacion.REVISION_MANUAL) {
            return new ResultadoRegistro(HttpStatus.ACCEPTED, cuerpo(a, ev, "Siguen faltando datos del lote: continúa pendiente"));
        }
        return new ResultadoRegistro(HttpStatus.UNPROCESSABLE_ENTITY,
                cuerpo(a, ev, "La reevaluación no la recomienda: continúa pendiente (se puede autorizar manualmente)"));
    }

    /** Regla A1: autorización manual con justificación obligatoria, solo de una aplicación pendiente. */
    public AplicacionResponse autorizar(Long id, AutorizacionRequest r, Usuario u) {
        Aplicacion a = buscar(id);
        exigirPendiente(a);
        a.setEstado(EstadoAplicacion.AUTORIZADA_MANUALMENTE);
        a.setJustificacion(r.justificacion().trim());
        a.setAutorizadaPorSub(u.sub());
        aplicaciones.save(a);
        return aRespuesta(a);
    }

    // ------------------------------------------------------------------ F08: consultar

    /** El agrónomo ve todas; el productor las de sus establecimientos; el operario solo las consulta por id. */
    public List<AplicacionResponse> listar(EstadoAplicacion estado, Usuario u) {
        List<Aplicacion> lista;
        if (u.esAgronomo()) {
            lista = estado == null ? aplicaciones.findAllByOrderByIdDesc() : aplicaciones.findByEstadoOrderByIdDesc(estado);
        } else if (u.esProductor()) {
            lista = estado == null ? aplicaciones.findByProductor(u.sub()) : aplicaciones.findByProductorYEstado(u.sub(), estado);
        } else {
            throw new AccessDeniedException("El operario consulta las aplicaciones solo por id");
        }
        List<AplicacionResponse> respuesta = new ArrayList<>();
        for (Aplicacion a : lista) {
            respuesta.add(aRespuesta(a));
        }
        return respuesta;
    }

    public AplicacionResponse obtener(Long id, Usuario u) {
        Aplicacion a = buscar(id);
        acceso.exigirLectura(a.getCampania().getLote().getEstablecimiento(), u);
        return aRespuesta(a);
    }

    // ------------------------------------------------------------------ auxiliares

    private Aplicacion buscar(Long id) {
        return aplicaciones.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe la aplicación " + id));
    }

    private void exigirPendiente(Aplicacion a) {
        if (a.getEstado() != EstadoAplicacion.PENDIENTE_DE_VALIDACION) {
            throw ErroresDeRegla.de("A1", "La aplicación no está pendiente de validación (estado: " + a.getEstado() + ")");
        }
    }

    private void copiarEvaluacion(Aplicacion a, EvaluacionResponse ev) {
        a.setEvaluacionId(ev.id());
        a.setDosisTotal(ev.dosisTotal());
        a.setEnvases(ev.envases());
        a.setCosto(ev.costo());
    }

    /**
     * Arma el pedido con el suelo y la ubicación del lote y el historial de ese producto en el lote.
     * El historial son solo las aplicaciones que de verdad se hicieron (REGISTRADA o AUTORIZADA_MANUALMENTE) del
     * mismo agroquímico: ms-tratamientos usa la fecha de la última para el intervalo mínimo (T3).
     * Sin suelo o sin ubicación en el lote, esos datos van en null y la decisión es REVISION_MANUAL (L1).
     */
    private EvaluacionRequest armarPedido(Campania c, Aplicacion a) {
        Lote l = c.getLote();

        SueloDto suelo = null;
        if (l.getEstadoSuelo() == EstadoSuelo.OK && l.getClaseTextura() != null) {
            suelo = new SueloDto(l.getClaseTextura(), l.getArcillaPct(), l.getArenaPct(), l.getLimoPct(), l.getPh(),
                    l.getCarbonoOrganico());
        }
        UbicacionDto ubicacion = null;
        if (l.getProvincia() != null && !l.getProvincia().isBlank()) {
            ubicacion = new UbicacionDto(l.getProvincia(), l.getDepartamento(), null);
        }

        List<AplicacionPreviaDto> previas = new ArrayList<>();
        List<Aplicacion> hechas = aplicaciones.findByLoteYEstados(l.getId(),
                List.of(EstadoAplicacion.REGISTRADA, EstadoAplicacion.AUTORIZADA_MANUALMENTE));
        for (Aplicacion previa : hechas) {
            boolean mismoProducto = Objects.equals(previa.getAgroquimicoId(), a.getAgroquimicoId());
            boolean otraAplicacion = !Objects.equals(previa.getId(), a.getId());
            if (mismoProducto && otraAplicacion) {
                previas.add(new AplicacionPreviaDto(previa.getFecha()));
            }
        }

        return new EvaluacionRequest(c.getCultivoId(), a.getPlagaId(), a.getAgroquimicoId(), a.getDosisPorHa(),
                a.getSuperficieTratadaHa(), a.getFecha(), c.getCosechaEstimada(), suelo, ubicacion, previas);
    }

    private AplicacionResultadoResponse cuerpo(Aplicacion a, EvaluacionResponse ev, String mensaje) {
        return new AplicacionResultadoResponse(a == null ? null : a.getId(), a == null ? null : a.getEstado(),
                ev.resultado(), ev.id(), ev.fechaSugerida(), ev.dosisTotal(), ev.envases(), ev.costo(),
                ev.motivos(), mensaje);
    }

    private AplicacionResponse aRespuesta(Aplicacion a) {
        return new AplicacionResponse(a.getId(), a.getCampania().getId(), a.getAgroquimicoId(), a.getPlagaId(),
                a.getDosisPorHa(), a.getSuperficieTratadaHa(), a.getFecha(), a.getEstado(), a.getEvaluacionId(),
                a.getDosisTotal(), a.getEnvases(), a.getCosto(), a.getJustificacion(), a.getAutorizadaPorSub(),
                a.getRegistradaPorSub());
    }
}
