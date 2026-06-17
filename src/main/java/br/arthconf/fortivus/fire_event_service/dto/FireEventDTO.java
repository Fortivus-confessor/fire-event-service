package br.arthconf.fortivus.fire_event_service.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class FireEventDTO {
    private String id;
    private String externalId;
    private String satellite;
    private String confidence;
    private Double frp;
    private LocalDateTime acquisitionDate;
    private Double latitude;
    private Double longitude;
}
