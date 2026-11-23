package ar.edu.agro.campos.repository;

import ar.edu.agro.campos.model.Monitoreo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MonitoreoRepository extends JpaRepository<Monitoreo, Long> {
    List<Monitoreo> findByCampaniaIdOrderByFechaAscIdAsc(Long campaniaId);
}
