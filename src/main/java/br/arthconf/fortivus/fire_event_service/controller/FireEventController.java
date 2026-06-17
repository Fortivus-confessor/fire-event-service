package br.arthconf.fortivus.fire_event_service.controller;

import br.arthconf.fortivus.fire_event_service.domain.FireEvent;
import br.arthconf.fortivus.fire_event_service.dto.FireEventDTO;
import br.arthconf.fortivus.fire_event_service.repository.FireEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/fire-events")
@RequiredArgsConstructor
public class FireEventController {

    private final FireEventRepository repository;

    @GetMapping("/latest")
    public ResponseEntity<List<FireEventDTO>> getLatest() {
        // Fetch top 50 critical fire events by FRP
        Page<FireEvent> page = repository.findAll(PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "frp")));
        
        List<FireEventDTO> dtos = page.getContent().stream().map(event -> {
            FireEventDTO dto = new FireEventDTO();
            dto.setId(event.getId().toString());
            dto.setExternalId(event.getExternalId());
            dto.setSatellite(event.getSatellite());
            dto.setConfidence(event.getConfidence());
            dto.setFrp(event.getFrp());
            dto.setAcquisitionDate(event.getAcquisitionDate());
            if (event.getGeom() != null) {
                dto.setLatitude(event.getGeom().getY());
                dto.setLongitude(event.getGeom().getX());
            }
            return dto;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }
}
