package br.arthconf.fortivus.fire_event_service.service;

import br.arthconf.fortivus.fire_event_service.domain.FocoCalor;
import br.arthconf.fortivus.fire_event_service.repository.FocoCalorRepository;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.StringReader;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Slf4j
public class NasaIngestionService {

    private final FocoCalorRepository focoCalorRepository;
    private final ClusterizacaoService clusterizacaoService;
    private final GravidadeScoreService gravidadeScoreService;

    @Value("${app.nasa-firms.url}")
    private String nasaUrl;

    @Value("${app.nasa-firms.map-key}")
    private String mapKey;

    private final GeometryFactory geometryFactory = new GeometryFactory();

    // Filtro Bounding Box - Brasil
    private static final double LAT_MIN = -33.7;
    private static final double LAT_MAX = 5.2;
    private static final double LON_MIN = -74.0;
    private static final double LON_MAX = -34.8;

    @Scheduled(cron = "${app.sync.cron}")
    public void sincronizarDadosNasa() {
        log.info("Iniciando sincronização de dados da NASA FIRMS...");
        try {
            RestTemplate restTemplate = new RestTemplate();
            // A URL real do FIRMS usa a Map Key e formata o pais/area. Aqui simularemos a chamada direta a URL baseada em pais (BRA) ou bbox
            String fullUrl = nasaUrl.replace("YOUR_NASA_FIRMS_MAP_KEY", mapKey); // Ajuste conforme a URL final real do FIRMS
            
            // Para simplificar a POC, em caso de erro da URL, logamos e retornamos.
            if(fullUrl.contains("YOUR_NASA_FIRMS_MAP_KEY")) {
                log.warn("Nasa Map Key nao configurada. Ignorando sincronizacao real.");
                return;
            }

            String csvContent = restTemplate.getForObject(fullUrl, String.class);
            if (csvContent != null) {
                processarCsv(csvContent);
            }
        } catch (Exception e) {
            log.error("Erro ao buscar dados da NASA", e);
        }
    }

    public void processarCsv(String csvContent) {
        try (CSVReader csvReader = new CSVReader(new StringReader(csvContent))) {
            String[] headers = csvReader.readNext(); // Ignora cabeçalho
            String[] record;

            while ((record = csvReader.readNext()) != null) {
                // Mapeamento FIRMS VIIRS/MODIS: lat(0), lon(1), brightness(2), ..., acq_date(5), acq_time(6), satellite(7), instrument(8), confidence(9), frp(12)
                double lat = Double.parseDouble(record[0]);
                double lon = Double.parseDouble(record[1]);
                String confidence = record[9];
                double frp = Double.parseDouble(record[12]);

                // 1. Filtro de Ingestão: Qualidade
                if ("low".equalsIgnoreCase(confidence) || "l".equalsIgnoreCase(confidence) || isConfidenceTooLow(confidence)) {
                    continue; // Ignora falsos positivos
                }

                // 2. Filtro de Ingestão: Espacial (Apenas Brasil)
                if (lat < LAT_MIN || lat > LAT_MAX || lon < LON_MIN || lon > LON_MAX) {
                    continue; // Fora do Brasil
                }

                String acqDate = record[5];
                String acqTime = record[6]; // Ex: "1430" (14:30)
                String satellite = record[7];
                String instrument = record[8];

                String externalId = gerarExternalId(satellite, acqDate, acqTime, lat, lon);

                // Evita duplicidade se já ingeriu
                if (focoCalorRepository.existsByExternalId(externalId)) {
                    continue;
                }

                FocoCalor foco = parseToFocoCalor(lat, lon, acqDate, acqTime, satellite, instrument, confidence, frp, externalId);
                
                // 3. Clusterização
                var eventoAlvo = clusterizacaoService.processarFoco(foco);

                // 4. Score de Gravidade
                gravidadeScoreService.avaliarGravidade(eventoAlvo);
            }

            log.info("Processamento do CSV da NASA concluído com sucesso.");
        } catch (Exception e) {
            log.error("Falha ao parsear CSV", e);
        }
    }

    private boolean isConfidenceTooLow(String confidence) {
        try {
            int confValue = Integer.parseInt(confidence);
            return confValue < 50;
        } catch (NumberFormatException e) {
            return false; // Se nao for numero, confia no filtro de string ("low")
        }
    }

    private String gerarExternalId(String satellite, String date, String time, double lat, double lon) {
        return String.format("%s_%s_%s_%.4f_%.4f", satellite, date, time, lat, lon);
    }

    private FocoCalor parseToFocoCalor(double lat, double lon, String dateStr, String timeStr, String sat, String inst, String conf, double frp, String externalId) {
        FocoCalor foco = new FocoCalor();
        foco.setExternalId(externalId);
        foco.setSatelite(sat);
        foco.setInstrumento(inst);
        foco.setConfidence(conf);
        foco.setFrp(frp);
        
        Point pt = geometryFactory.createPoint(new Coordinate(lon, lat));
        pt.setSRID(4326);
        foco.setGeom(pt);

        LocalDate date = LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String formattedTime = String.format("%04d", Integer.parseInt(timeStr));
        LocalTime time = LocalTime.parse(formattedTime, DateTimeFormatter.ofPattern("HHmm"));
        foco.setDataDeteccao(LocalDateTime.of(date, time));

        return foco;
    }
}
