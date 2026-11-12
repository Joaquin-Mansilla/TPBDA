package ar.edu.agro.tratamientos.repository;

import ar.edu.agro.tratamientos.model.RestriccionTerritorial;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RestriccionTerritorialRepository extends JpaRepository<RestriccionTerritorial, Long> {
    List<RestriccionTerritorial> findByAgroquimicoId(Long agroquimicoId);
    void deleteByAgroquimicoId(Long agroquimicoId);
}
