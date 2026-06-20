package br.arthconf.fortivus.fire_event_service.service;

import br.arthconf.fortivus.fire_event_service.domain.EventoFogo;
import br.arthconf.fortivus.fire_event_service.domain.FocoCalor;
import br.arthconf.fortivus.fire_event_service.repository.EventoFogoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ClusterizacaoService {

    private final EventoFogoRepository eventoFogoRepository;

    // Aproximação de 1.5km em graus decimais (1 grau = ~111km) -> 1.5 / 111 = 0.0135
    private static final double RAIO_BUSCA_GRAUS = 0.0135;

    @Transactional
    public EventoFogo processarFoco(FocoCalor novoFoco) {
        // Busca eventos ativos próximos
        List<EventoFogo> eventosProximos = eventoFogoRepository.findActiveEventsNear(
                novoFoco.getGeom().getX(), // longitude
                novoFoco.getGeom().getY(), // latitude
                RAIO_BUSCA_GRAUS
        );

        EventoFogo eventoAlvo;

        if (eventosProximos.isEmpty()) {
            // Cria um novo evento se não achar nenhum próximo
            eventoAlvo = new EventoFogo();
            eventoAlvo.setCentroideGeom(novoFoco.getGeom());
            eventoAlvo.setDataPrimeiraDeteccao(novoFoco.getDataDeteccao());
            eventoAlvo.setDataUltimaDeteccao(novoFoco.getDataDeteccao());
            eventoAlvo.setCodigo(gerarProximoCodigo());
        } else {
            // Associa ao primeiro evento próximo encontrado
            eventoAlvo = eventosProximos.get(0);
        }

        eventoAlvo.adicionarFoco(novoFoco);
        return eventoFogoRepository.save(eventoAlvo);
    }

    private Long gerarProximoCodigo() {
        long anoAtual = java.time.LocalDateTime.now().getYear();
        long minId = anoAtual * 100000000L;
        long maxId = minId + 99999999L;
        
        Long maxExistente = eventoFogoRepository.findMaxCodigoByAno(minId, maxId);
        return (maxExistente == null || maxExistente < minId) ? minId + 1 : maxExistente + 1;
    }
}
