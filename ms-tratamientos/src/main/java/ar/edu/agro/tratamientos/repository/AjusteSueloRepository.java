package ar.edu.agro.tratamientos.repository;

import ar.edu.agro.commons.contract.ClaseTextura;
import ar.edu.agro.tratamientos.model.AjusteSuelo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AjusteSueloRepository extends JpaRepository<AjusteSuelo, Long> {
    Optional<AjusteSuelo> findByAgroquimicoIdAndClaseTextura(Long agroquimicoId, ClaseTextura claseTextura);
    List<AjusteSuelo> findByAgroquimicoId(Long agroquimicoId);
    void deleteByAgroquimicoId(Long agroquimicoId);
}
