package br.arthconf.fortivus.fire_event_service.service;

import br.arthconf.fortivus.fire_event_service.domain.EventoFogo;
import br.arthconf.fortivus.fire_event_service.domain.StatusEventoEnum;
import br.arthconf.fortivus.fire_event_service.dto.EventoSeveroDTO;
import br.arthconf.fortivus.fire_event_service.repository.EventoFogoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class GravidadeScoreService {

    private final EventoFogoRepository eventoFogoRepository;
    private final RabbitTemplate rabbitTemplate;

    private static final double LIMIAR_FRP_CRITICO = 150.0;
    private static final int LIMIAR_FOCOS_CRITICO = 3;
    private static final String EXCHANGE_NAME = "fortivus.fire_events";
    private static final String ROUTING_KEY = "fire.detected.severe";

    @Transactional
    public void avaliarGravidade(EventoFogo evento) {
        if (evento.getStatusEvento() == StatusEventoEnum.MONITORAMENTO) {
            
            boolean isSevero = evento.getFrpTotal() >= LIMIAR_FRP_CRITICO || evento.getTotalFocos() >= LIMIAR_FOCOS_CRITICO;

            if (isSevero) {
                log.warn("Evento de fogo {} classificado como ATIVO_SEVERO. FRP Total: {}", evento.getId(), evento.getFrpTotal());
                evento.setStatusEvento(StatusEventoEnum.ATIVO_SEVERO);
                eventoFogoRepository.save(evento);
                
                dispararAlertaRabbitMQ(evento);
            }
        }
    }

    private void dispararAlertaRabbitMQ(EventoFogo evento) {
        EventoSeveroDTO dto = EventoSeveroDTO.builder()
                .eventoId(evento.getId())
                .latitudeCentroide(evento.getCentroideGeom().getY())
                .longitudeCentroide(evento.getCentroideGeom().getX())
                .frpTotal(evento.getFrpTotal())
                .totalFocos(evento.getTotalFocos())
                .dataDeteccao(evento.getDataUltimaDeteccao())
                .severidade(evento.getFrpTotal() > 500 ? "CRITICA" : "ALTA")
                .build();

        rabbitTemplate.convertAndSend(EXCHANGE_NAME, ROUTING_KEY, dto);
        log.info("Alerta de evento severo enviado para o RabbitMQ (Routing Key: {})", ROUTING_KEY);
    }
}
