package ar.edu.agro.campos.repository;

import ar.edu.agro.campos.model.Aplicacion;
import ar.edu.agro.campos.model.EstadoAplicacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface AplicacionRepository extends JpaRepository<Aplicacion, Long> {

    /** Historial del lote para la regla T3: solo aplicaciones que de verdad se hicieron. */
    @Query("select a from Aplicacion a where a.campania.lote.id = :loteId and a.estado in :estados")
    List<Aplicacion> findByLoteYEstados(@Param("loteId") Long loteId, @Param("estados") Collection<EstadoAplicacion> estados);

    List<Aplicacion> findByEstadoOrderByIdDesc(EstadoAplicacion estado);

    List<Aplicacion> findAllByOrderByIdDesc();

    @Query("select a from Aplicacion a where a.campania.lote.establecimiento.productorSub = :sub order by a.id desc")
    List<Aplicacion> findByProductor(@Param("sub") String sub);

    @Query("select a from Aplicacion a where a.campania.lote.establecimiento.productorSub = :sub and a.estado = :estado order by a.id desc")
    List<Aplicacion> findByProductorYEstado(@Param("sub") String sub, @Param("estado") EstadoAplicacion estado);
}
