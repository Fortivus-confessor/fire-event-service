package br.arthconf.fortivus.fire_event_service.domain;

import jakarta.persistence.*;
import lombok.Data;
import org.locationtech.jts.geom.Point;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "fire_event", indexes = {
        @Index(name = "idx_fire_event_geom", columnList = "geom")
})
@Data
public class FireEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "external_id", unique = true)
    private String externalId;

    private String satellite;

    private String confidence;

    private Double frp;

    @Column(name = "acquisition_date")
    private LocalDateTime acquisitionDate;

    @Column(columnDefinition = "geometry(Point,4326)")
    private Point geom;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
