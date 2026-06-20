package br.arthconf.fortivus.fire_event_service.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.locationtech.jts.geom.Point;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "tb_eventos_fogo", indexes = {
        @Index(name = "idx_evento_fogo_geom", columnList = "centroide_geom")
})
@Data
@EqualsAndHashCode(of = "id")
public class EventoFogo {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "codigo", unique = true)
    private Long codigo;

    @Column(name = "centroide_geom", columnDefinition = "geometry(Point,4326)")
    private Point centroideGeom;

    @Column(name = "data_primeira_deteccao", nullable = false)
    private LocalDateTime dataPrimeiraDeteccao;

    @Column(name = "data_ultima_deteccao", nullable = false)
    private LocalDateTime dataUltimaDeteccao;

    @Column(name = "frp_total", nullable = false)
    private Double frpTotal = 0.0;

    @Column(name = "frp_maximo", nullable = false)
    private Double frpMaximo = 0.0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_evento", nullable = false)
    private StatusEventoEnum statusEvento = StatusEventoEnum.MONITORAMENTO;

    @Column(name = "total_focos", nullable = false)
    private Integer totalFocos = 0;

    @OneToMany(mappedBy = "eventoFogo", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<FocoCalor> focos = new ArrayList<>();
    
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public void adicionarFoco(FocoCalor foco) {
        this.focos.add(foco);
        foco.setEventoFogo(this);
        this.totalFocos++;
        this.frpTotal += foco.getFrp();
        if (foco.getFrp() > this.frpMaximo) {
            this.frpMaximo = foco.getFrp();
        }
        if (foco.getDataDeteccao().isAfter(this.dataUltimaDeteccao)) {
            this.dataUltimaDeteccao = foco.getDataDeteccao();
        }
    }
}
