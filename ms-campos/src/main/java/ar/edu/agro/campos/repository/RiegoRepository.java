package ar.edu.agro.campos.repository;

import ar.edu.agro.campos.model.Riego;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RiegoRepository extends JpaRepository<Riego, Long> {
    List<Riego> findByCampaniaIdOrderByFechaAsc(Long campaniaId);
}
