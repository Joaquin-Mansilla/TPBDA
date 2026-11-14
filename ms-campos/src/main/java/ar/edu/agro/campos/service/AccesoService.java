package ar.edu.agro.campos.service;

import ar.edu.agro.campos.model.Establecimiento;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/** Regla C6: cada usuario accede solo a lo suyo (el productor a sus establecimientos, el operario a los asignados). */
@Service
public class AccesoService {

    /** Leer: PRODUCTOR dueño, OPERARIO asignado o AGRONOMO (ve todo). */
    public void exigirLectura(Establecimiento e, Usuario u) {
        if (!(u.esAgronomo() || esDueno(e, u) || esAsignado(e, u))) {
            throw new AccessDeniedException("El establecimiento no pertenece al usuario");
        }
    }

    /** Modificar: solo el PRODUCTOR dueño. */
    public void exigirDueno(Establecimiento e, Usuario u) {
        if (!esDueno(e, u)) {
            throw new AccessDeniedException("Solo el productor dueño puede hacer esta operación");
        }
    }

    /** Registrar monitoreos y aplicaciones: PRODUCTOR dueño u OPERARIO asignado. */
    public void exigirDuenoOAsignado(Establecimiento e, Usuario u) {
        if (!(esDueno(e, u) || esAsignado(e, u))) {
            throw new AccessDeniedException("El usuario no es dueño ni operario asignado del establecimiento");
        }
    }

    private boolean esDueno(Establecimiento e, Usuario u) {
        return u.esProductor() && e.getProductorSub().equals(u.sub());
    }

    private boolean esAsignado(Establecimiento e, Usuario u) {
        return u.esOperario() && e.getOperarios().contains(u.sub());
    }
}
