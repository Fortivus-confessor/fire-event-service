package br.arthconf.fortivus.fire_event_service.service;

import br.arthconf.fortivus.fire_event_service.domain.EventoFogo;
import br.arthconf.fortivus.fire_event_service.domain.SeveridadeEventoEnum;
import br.arthconf.fortivus.fire_event_service.domain.StatusEventoEnum;
import br.arthconf.fortivus.fire_event_service.dto.EventoSeveroDTO;
import br.arthconf.fortivus.fire_event_service.repository.EventoFogoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class GravidadeScoreService {

    private final EventoFogoRepository eventoFogoRepository;
    private final RabbitTemplate rabbitTemplate;

    // Limiar científico para ALTA: FRP ≥ 150 MW (categoria 2 de Ichoku et al., 2008)
    // ou ≥ 3 focos co-localizados (Schroeder et al., 2010 — indicador de frente estendida)
    private static final double LIMIAR_FRP_ALTA = 150.0;
    private static final int LIMIAR_FOCOS_ALTA = 3;

    // Limiar científico para CRITICA: FRP > 500 MW (categoria 3 de Ichoku et al., 2008;
    // consumo de biomassa ≈ 184 kg/s segundo Wooster et al., 2005)
    private static final double LIMIAR_FRP_CRITICA = 500.0;

    private static final String EXCHANGE_NAME = "fortivus.fire_events";
    private static final String ROUTING_KEY = "fire.detected.severe";

    @Transactional
    public void avaliarGravidade(EventoFogo evento) {
        if (evento.getStatusEvento() != StatusEventoEnum.MONITORAMENTO) {
            return;
        }

        SeveridadeEventoEnum severidade = classificarSeveridade(evento);
        if (severidade == null) {
            return;
        }

        log.warn("Evento {} classificado como ATIVO_SEVERO / {}. FRP Total: {} MW, Focos: {}",
                evento.getId(), severidade, evento.getFrpTotal(), evento.getTotalFocos());

        evento.setStatusEvento(StatusEventoEnum.ATIVO_SEVERO);
        evento.setSeveridade(severidade);
        eventoFogoRepository.save(evento);

        dispararAlertaRabbitMQ(evento);
    }

    private SeveridadeEventoEnum classificarSeveridade(EventoFogo evento) {
        if (evento.getFrpTotal() > LIMIAR_FRP_CRITICA) {
            return SeveridadeEventoEnum.CRITICA;
        }
        if (evento.getFrpTotal() >= LIMIAR_FRP_ALTA || evento.getTotalFocos() >= LIMIAR_FOCOS_ALTA) {
            return SeveridadeEventoEnum.ALTA;
        }
        return null;
    }

    private void dispararAlertaRabbitMQ(EventoFogo evento) {
        try {
            EventoSeveroDTO dto = EventoSeveroDTO.builder()
                    .eventoId(evento.getId())
                    .latitudeCentroide(evento.getCentroideGeom().getY())
                    .longitudeCentroide(evento.getCentroideGeom().getX())
                    .frpTotal(evento.getFrpTotal())
                    .totalFocos(evento.getTotalFocos())
                    .dataDeteccao(evento.getDataUltimaDeteccao())
                    .severidade(evento.getSeveridade())
                    .build();

            rabbitTemplate.convertAndSend(EXCHANGE_NAME, ROUTING_KEY, dto);
            log.info("Alerta de evento severo enviado para o RabbitMQ (Routing Key: {})", ROUTING_KEY);
        } catch (AmqpException e) {
            log.error("Falha ao publicar alerta RabbitMQ para evento {} — status ATIVO_SEVERO persistido no banco, alerta perdido: {}",
                    evento.getId(), e.getMessage());
        }
    }
}
