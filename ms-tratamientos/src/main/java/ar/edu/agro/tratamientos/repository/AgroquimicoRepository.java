package ar.edu.agro.tratamientos.repository;

import ar.edu.agro.tratamientos.model.Agroquimico;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AgroquimicoRepository extends JpaRepository<Agroquimico, Long> {
    List<Agroquimico> findByActivoTrueOrderByNombre();
    boolean existsByNombreIgnoreCase(String nombre);
    boolean existsByNombreIgnoreCaseAndIdNot(String nombre, Long id);
}
