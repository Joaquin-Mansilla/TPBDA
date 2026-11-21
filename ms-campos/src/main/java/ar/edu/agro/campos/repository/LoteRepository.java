package ar.edu.agro.campos.repository;

import ar.edu.agro.campos.model.Lote;
import java.math.BigDecimal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoteRepository extends JpaRepository<Lote, Long> {

    /** Hectáreas ya ocupadas por los lotes de un establecimiento (C2, T16). */
    @Query("select coalesce(sum(l.superficieHa), 0) from Lote l where l.establecimiento.id = :establecimientoId")
    BigDecimal sumarSuperficie(@Param("establecimientoId") Long establecimientoId);
}
