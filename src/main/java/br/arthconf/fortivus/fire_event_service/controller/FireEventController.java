package br.arthconf.fortivus.fire_event_service.controller;

import br.arthconf.fortivus.fire_event_service.domain.EventoFogo;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.persistence.EntityManager;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import br.arthconf.fortivus.fire_event_service.repository.EventoFogoRepository;


@RestController
@RequestMapping("/api/v1/fire-events")
@RequiredArgsConstructor
public class FireEventController {

    private final EntityManager entityManager;
    private final EventoFogoRepository eventoFogoRepository;

    @GetMapping
    public ResponseEntity<Page<EventoFogoDTO>> listar(
            @PageableDefault(sort = "dataUltimaDeteccao", direction = Sort.Direction.DESC, size = 10) Pageable pageable) {
        
        Page<br.arthconf.fortivus.fire_event_service.domain.EventoFogo> page = eventoFogoRepository.findAll(pageable);
        
        Page<EventoFogoDTO> dtoPage = page.map(e -> {
            EventoFogoDTO dto = new EventoFogoDTO();
            dto.setId(e.getId().toString());
            dto.setCodigo(e.getId() != null ? e.getId().toString() : null);
            dto.setCodigoVisual(e.getId() != null ? "EF" + e.getId().toString() : null);
            dto.setStatus(e.getStatusEvento().name());
            dto.setFrpTotal(e.getFrpTotal());
            dto.setTotalFocos(e.getTotalFocos());
            dto.setPrimeiraDeteccao(e.getDataPrimeiraDeteccao() != null ? e.getDataPrimeiraDeteccao().toString() : null);
            dto.setUltimaDeteccao(e.getDataUltimaDeteccao() != null ? e.getDataUltimaDeteccao().toString() : null);
            if (e.getCentroideGeom() != null) {
                dto.setLatitude(e.getCentroideGeom().getY());
                dto.setLongitude(e.getCentroideGeom().getX());
            }
            return dto;
        });
        
        return ResponseEntity.ok(dtoPage);
    }
    @GetMapping("/buscar")
    public ResponseEntity<List<EventoFogoDTO>> buscar(@org.springframework.web.bind.annotation.RequestParam("q") String q) {
        String cleanQuery = q.replaceAll("-", "");
        List<br.arthconf.fortivus.fire_event_service.domain.EventoFogo> eventos = entityManager.createQuery(
                "SELECT e FROM EventoFogo e WHERE CAST(e.id AS string) LIKE :query ORDER BY e.createdAt DESC", 
                br.arthconf.fortivus.fire_event_service.domain.EventoFogo.class)
                .setParameter("query", "%" + cleanQuery + "%")
                .setMaxResults(10)
                .getResultList();

        List<EventoFogoDTO> dtos = eventos.stream().map(e -> {
            EventoFogoDTO dto = new EventoFogoDTO();
            dto.setId(e.getId().toString());
            dto.setCodigo(e.getId() != null ? e.getId().toString() : null);
            dto.setCodigoVisual(e.getId() != null ? "EF" + e.getId().toString() : null);
            dto.setStatus(e.getStatusEvento().name());
            dto.setFrpTotal(e.getFrpTotal());
            dto.setTotalFocos(e.getTotalFocos());
            dto.setPrimeiraDeteccao(e.getDataPrimeiraDeteccao() != null ? e.getDataPrimeiraDeteccao().toString() : null);
            dto.setUltimaDeteccao(e.getDataUltimaDeteccao() != null ? e.getDataUltimaDeteccao().toString() : null);
            if (e.getCentroideGeom() != null) {
                dto.setLatitude(e.getCentroideGeom().getY());
                dto.setLongitude(e.getCentroideGeom().getX());
            }
            return dto;
        }).toList();

        return ResponseEntity.ok(dtos);
    }

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

    @GetMapping("/active")
    public ResponseEntity<List<EventoFogoDTO>> getActiveEvents() {
        // Approximate bounding box for Brazil
        double minLat = -33.7511694;
        double maxLat = 5.2718411;
        double minLng = -73.9828305;
        double maxLng = -34.7931472;

        List<br.arthconf.fortivus.fire_event_service.domain.EventoFogo> eventos = entityManager.createQuery(
                "SELECT e FROM EventoFogo e WHERE e.statusEvento != 'EXTINTO' AND ST_Y(e.centroideGeom) BETWEEN :minLat AND :maxLat AND ST_X(e.centroideGeom) BETWEEN :minLng AND :maxLng", br.arthconf.fortivus.fire_event_service.domain.EventoFogo.class)
                .setParameter("minLat", minLat)
                .setParameter("maxLat", maxLat)
                .setParameter("minLng", minLng)
                .setParameter("maxLng", maxLng)
                .getResultList();

        List<EventoFogoDTO> dtos = eventos.stream().map(e -> {
            EventoFogoDTO dto = new EventoFogoDTO();
            dto.setId(e.getId().toString());
            dto.setCodigo(e.getId() != null ? e.getId().toString() : null);
            dto.setCodigoVisual(e.getId() != null ? "EF" + e.getId().toString() : null);
            dto.setStatus(e.getStatusEvento().name());
            dto.setFrpTotal(e.getFrpTotal());
            dto.setTotalFocos(e.getTotalFocos());
            dto.setPrimeiraDeteccao(e.getDataPrimeiraDeteccao() != null ? e.getDataPrimeiraDeteccao().toString() : null);
            dto.setUltimaDeteccao(e.getDataUltimaDeteccao() != null ? e.getDataUltimaDeteccao().toString() : null);
            if (e.getCentroideGeom() != null) {
                dto.setLatitude(e.getCentroideGeom().getY());
                dto.setLongitude(e.getCentroideGeom().getX());
            }
            // Mapear focos de calor do evento
            dto.setFocos(e.getFocos().stream().map(f -> {
                FocoCalorDTO fDto = new FocoCalorDTO();
                fDto.setId(f.getId().toString());
                fDto.setExternalId(f.getExternalId());
                fDto.setSatellite(f.getSatelite());
                fDto.setInstrument(f.getInstrumento());
                fDto.setConfidence(f.getConfidence());
                fDto.setFrp(f.getFrp());
                fDto.setAcquisitionDate(f.getDataDeteccao() != null ? f.getDataDeteccao().toString() : null);
                if (f.getGeom() != null) {
                    fDto.setLatitude(f.getGeom().getY());
                    fDto.setLongitude(f.getGeom().getX());
                }
                return fDto;
            }).toList());
            return dto;
        }).toList();

        return ResponseEntity.ok(dtos);
    }

    @lombok.Data
    public static class EventoFogoDTO {
        private String id;
        private String codigo;
        private String codigoVisual;
        private Double latitude;
        private Double longitude;
        private String status;
        private Double frpTotal;
        private Integer totalFocos;
        private String primeiraDeteccao;
        private String ultimaDeteccao;
        private List<FocoCalorDTO> focos;
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
