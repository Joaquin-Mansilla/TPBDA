package ar.edu.agro.campos.repository;

import ar.edu.agro.campos.model.Lluvia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LluviaRepository extends JpaRepository<Lluvia, Long> {
    List<Lluvia> findByLoteIdOrderByFechaAscIdAsc(Long loteId);
}
