package ar.edu.agro.tratamientos.repository;

import ar.edu.agro.tratamientos.model.Evaluacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EvaluacionRepository extends JpaRepository<Evaluacion, Long> {
}
