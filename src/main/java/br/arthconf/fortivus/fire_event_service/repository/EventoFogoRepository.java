package br.arthconf.fortivus.fire_event_service.repository;

import br.arthconf.fortivus.fire_event_service.domain.EventoFogo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EventoFogoRepository extends JpaRepository<EventoFogo, UUID> {
    
    // Busca eventos ativos que estão dentro de uma certa distância de um ponto (usando PostGIS)
    @Query(value = "SELECT * FROM fire_events.tb_eventos_fogo e WHERE e.status_evento != 'EXTINTO' AND ST_DWithin(e.centroide_geom, ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326), :radiusInDegrees)", nativeQuery = true)
    List<EventoFogo> findActiveEventsNear(double longitude, double latitude, double radiusInDegrees);

    @Query("SELECT MAX(e.codigo) FROM EventoFogo e WHERE e.codigo >= :minCodigo AND e.codigo <= :maxCodigo")
    Long findMaxCodigoByAno(Long minCodigo, Long maxCodigo);
}
