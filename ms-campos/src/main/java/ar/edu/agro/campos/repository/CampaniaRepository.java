package ar.edu.agro.campos.repository;

import ar.edu.agro.campos.model.Campania;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CampaniaRepository extends JpaRepository<Campania, Long> {
    List<Campania> findByLoteIdOrderByFechaSiembraDesc(Long loteId);
}
