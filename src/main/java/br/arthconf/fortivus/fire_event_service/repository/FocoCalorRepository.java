package br.arthconf.fortivus.fire_event_service.repository;

import br.arthconf.fortivus.fire_event_service.domain.FocoCalor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface FocoCalorRepository extends JpaRepository<FocoCalor, UUID> {
    boolean existsByExternalId(String externalId);
}
