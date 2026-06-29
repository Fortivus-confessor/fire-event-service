package br.arthconf.fortivus.fire_event_service.repository;

import br.arthconf.fortivus.fire_event_service.domain.EventoFogo;
import br.arthconf.fortivus.fire_event_service.domain.StatusEventoEnum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface EventoFogoRepository extends JpaRepository<EventoFogo, Long> {

    // Busca eventos ativos que estão dentro de uma certa distância de um ponto (usando PostGIS)
    @Query(value = "SELECT * FROM fire_events.tb_eventos_fogo e WHERE e.status_evento != 'EXTINTO' AND ST_DWithin(e.centroide_geom, ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326), :radiusInDegrees)", nativeQuery = true)
    List<EventoFogo> findActiveEventsNear(double longitude, double latitude, double radiusInDegrees);

    @Query("SELECT MAX(e.id) FROM EventoFogo e WHERE e.id >= :minCodigo AND e.id <= :maxCodigo")
    Long findMaxCodigoByAno(Long minCodigo, Long maxCodigo);

    @Query("SELECT e FROM EventoFogo e WHERE e.statusEvento <> :extinto AND e.dataUltimaDeteccao < :limite")
    List<EventoFogo> findInativos(@Param("extinto") StatusEventoEnum extinto, @Param("limite") LocalDateTime limite);
}
