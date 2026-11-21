package ar.edu.agro.campos.model;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Estados de una campaña como máquina de estados (regla C5): cada estado conoce a cuáles puede pasar.
 * PLANIFICADA -> SEMBRADA o PERDIDA; SEMBRADA -> COSECHADA o PERDIDA; COSECHADA y PERDIDA son finales.
 */
public enum EstadoCampania {
    PLANIFICADA, SEMBRADA, COSECHADA, PERDIDA;

    private static final Map<EstadoCampania, Set<EstadoCampania>> SIGUIENTES = new EnumMap<>(EstadoCampania.class);
    static {
        SIGUIENTES.put(PLANIFICADA, EnumSet.of(SEMBRADA, PERDIDA));
        SIGUIENTES.put(SEMBRADA, EnumSet.of(COSECHADA, PERDIDA));
        SIGUIENTES.put(COSECHADA, EnumSet.noneOf(EstadoCampania.class));
        SIGUIENTES.put(PERDIDA, EnumSet.noneOf(EstadoCampania.class));
    }

    public boolean puedePasarA(EstadoCampania nuevo) {
        return SIGUIENTES.get(this).contains(nuevo);
    }

    public boolean esFinal() {
        return SIGUIENTES.get(this).isEmpty();
    }
}
