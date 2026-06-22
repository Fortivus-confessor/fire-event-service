package br.arthconf.fortivus.fire_event_service.service;

import br.arthconf.fortivus.fire_event_service.domain.StatusEventoEnum;
import br.arthconf.fortivus.fire_event_service.repository.EventoFogoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReavaliacaoEventoService {

    private final EventoFogoRepository eventoFogoRepository;

    // Tempo sem novos focos para considerar o incêndio como extinto (ex: 48 horas)
    private static final int HORAS_INATIVIDADE_EXTINCAO = 48;

    /**
     * Roda a cada 6 horas para varrer o banco e verificar eventos que não 
     * recebem novos focos de calor da NASA há mais de 48 horas.
     */
    @Scheduled(cron = "0 0 */6 * * *")
    @Transactional
    public void reavaliarEventosInativos() {
        log.info("Iniciando reavaliação de eventos de fogo inativos...");

        LocalDateTime limiteTempo = LocalDateTime.now().minusHours(HORAS_INATIVIDADE_EXTINCAO);

        // Busca eventos que ainda estão ativos mas não recebem atualização
        var eventosDesatualizados = eventoFogoRepository.findAll().stream()
                .filter(e -> e.getStatusEvento() != StatusEventoEnum.EXTINTO)
                .filter(e -> e.getDataUltimaDeteccao().isBefore(limiteTempo))
                .toList();

        if (!eventosDesatualizados.isEmpty()) {
            eventosDesatualizados.forEach(e -> {
                log.info("Evento de fogo {} não recebe focos há mais de {}h. Marcando como EXTINTO.", 
                        e.getId(), HORAS_INATIVIDADE_EXTINCAO);
                e.setStatusEvento(StatusEventoEnum.EXTINTO);
            });

            eventoFogoRepository.saveAll(eventosDesatualizados);
            log.info("{} eventos foram marcados como EXTINTO por inatividade térmica.", eventosDesatualizados.size());
        } else {
            log.info("Nenhum evento inativo para ser extinto no momento.");
        }
    }
}
