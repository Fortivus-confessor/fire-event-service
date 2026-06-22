package br.arthconf.fortivus.fire_event_service.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class EventoSeveroDTO {
    private Long eventoId;
    private Double latitudeCentroide;
    private Double longitudeCentroide;
    private Double frpTotal;
    private Integer totalFocos;
    private LocalDateTime dataDeteccao;
    private String severidade; // ex: ALTA, CRITICA
}
