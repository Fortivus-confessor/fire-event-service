package br.arthconf.fortivus.fire_event_service.controller;

import br.arthconf.fortivus.fire_event_service.domain.EventoFogo;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.persistence.EntityManager;
import java.util.List;

@RestController
@RequestMapping("/api/v1/fire-events")
@RequiredArgsConstructor
public class FireEventController {

    private final EntityManager entityManager;

    @GetMapping("/latest")
    public ResponseEntity<List<FocoCalorDTO>> getLatest() {
        List<br.arthconf.fortivus.fire_event_service.domain.FocoCalor> focos = entityManager.createQuery(
                "SELECT f FROM FocoCalor f ORDER BY f.dataDeteccao DESC", br.arthconf.fortivus.fire_event_service.domain.FocoCalor.class)
                .setMaxResults(50)
                .getResultList();

        List<FocoCalorDTO> dtos = focos.stream().map(f -> {
            FocoCalorDTO dto = new FocoCalorDTO();
            dto.setId(f.getId().toString());
            dto.setExternalId(f.getExternalId());
            dto.setSatellite(f.getSatelite());
            dto.setInstrument(f.getInstrumento());
            dto.setConfidence(f.getConfidence());
            dto.setFrp(f.getFrp());
            dto.setAcquisitionDate(f.getDataDeteccao() != null ? f.getDataDeteccao().toString() : null);
            if (f.getGeom() != null) {
                dto.setLatitude(f.getGeom().getY());
                dto.setLongitude(f.getGeom().getX());
            }
            return dto;
        }).toList();

        return ResponseEntity.ok(dtos);
    }

    @lombok.Data
    public static class FocoCalorDTO {
        private String id;
        private String externalId;
        private String satellite;
        private String instrument;
        private String confidence;
        private Double frp;
        private String acquisitionDate;
        private Double latitude;
        private Double longitude;
    }
}
