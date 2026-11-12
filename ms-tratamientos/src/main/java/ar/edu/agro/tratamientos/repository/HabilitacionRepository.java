package ar.edu.agro.tratamientos.repository;

import ar.edu.agro.tratamientos.model.Habilitacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HabilitacionRepository extends JpaRepository<Habilitacion, Long> {
    boolean existsByAgroquimicoIdAndCultivoIdAndPlagaId(Long agroquimicoId, Long cultivoId, Long plagaId);
    List<Habilitacion> findByAgroquimicoId(Long agroquimicoId);
    void deleteByAgroquimicoId(Long agroquimicoId);
}
