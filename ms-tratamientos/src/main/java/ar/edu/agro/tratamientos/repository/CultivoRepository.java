package ar.edu.agro.tratamientos.repository;

import ar.edu.agro.tratamientos.model.Cultivo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CultivoRepository extends JpaRepository<Cultivo, Long> {
    List<Cultivo> findByActivoTrueOrderByNombre();
    boolean existsByNombreIgnoreCase(String nombre);
    boolean existsByNombreIgnoreCaseAndIdNot(String nombre, Long id);
}
