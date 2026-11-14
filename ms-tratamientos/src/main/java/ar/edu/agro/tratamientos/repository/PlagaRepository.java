package ar.edu.agro.tratamientos.repository;

import ar.edu.agro.tratamientos.model.Plaga;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlagaRepository extends JpaRepository<Plaga, Long> {
    List<Plaga> findByActivoTrueOrderByNombre();
    boolean existsByNombreIgnoreCase(String nombre);
    boolean existsByNombreIgnoreCaseAndIdNot(String nombre, Long id);
}
