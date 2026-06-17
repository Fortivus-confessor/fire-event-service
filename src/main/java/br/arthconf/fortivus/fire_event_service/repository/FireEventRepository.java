package br.arthconf.fortivus.fire_event_service.repository;

import br.arthconf.fortivus.fire_event_service.domain.FireEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FireEventRepository extends JpaRepository<FireEvent, UUID> {
    Optional<FireEvent> findByExternalId(String externalId);
}
