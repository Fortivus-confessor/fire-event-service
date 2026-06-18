package br.arthconf.fortivus.fire_event_service.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.locationtech.jts.geom.Point;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tb_focos_calor", indexes = {
        @Index(name = "idx_foco_calor_geom", columnList = "geom")
})
@Data
@EqualsAndHashCode(of = "id")
public class FocoCalor {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "external_id", unique = true)
    private String externalId; // ID da NASA/FIRMS

    @Column(nullable = false)
    private String satelite;

    @Column(nullable = false)
    private String instrumento;

    private String confidence;

    @Column(nullable = false)
    private Double frp;

    @Column(name = "data_deteccao", nullable = false)
    private LocalDateTime dataDeteccao;

    @Column(columnDefinition = "geometry(Point,4326)", nullable = false)
    private Point geom;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evento_fogo_id", nullable = false)
    private EventoFogo eventoFogo;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
