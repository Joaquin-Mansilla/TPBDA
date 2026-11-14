package ar.edu.agro.campos.repository;

import ar.edu.agro.campos.model.Establecimiento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EstablecimientoRepository extends JpaRepository<Establecimiento, Long> {
    List<Establecimiento> findByProductorSubOrderById(String productorSub);

    @Query("select e from Establecimiento e where :sub member of e.operarios order by e.id")
    List<Establecimiento> findByOperario(@Param("sub") String sub);
}
