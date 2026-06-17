package br.arthconf.fortivus.fire_event_service.service;

import br.arthconf.fortivus.fire_event_service.config.RabbitMQConfig;
import br.arthconf.fortivus.fire_event_service.domain.FireEvent;
import br.arthconf.fortivus.fire_event_service.repository.FireEventRepository;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.StringReader;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class NasaFirmsSyncService {

    private final FireEventRepository fireEventRepository;
    private final RabbitTemplate rabbitTemplate;
    private final WebClient.Builder webClientBuilder;
    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

    @Value("${app.nasa-firms.url}")
    private String apiUrl;

    // Run every 30 minutes
    @Scheduled(fixedRateString = "1800000")
    public void syncFireEvents() {
        log.info("Iniciando sincronizacao com NASA FIRMS (South America 24h)...");

        // Public CSV link for South America (no API key required)
        String url = "https://firms.modaps.eosdis.nasa.gov/data/active_fire/noaa-20-viirs-c2/csv/J1_VIIRS_C2_South_America_24h.csv";

        try {
            String csvData = webClientBuilder.build()
                    .get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (csvData != null) {
                processCsvData(csvData);
            }
        } catch (Exception e) {
            log.error("Erro ao sincronizar com NASA FIRMS", e);
        }
    }

    private void processCsvData(String csvData) {
        try (CSVReader reader = new CSVReader(new StringReader(csvData))) {
            String[] headers = reader.readNext(); // skip header
            String[] line;
            int count = 0;
            while ((line = reader.readNext()) != null) {
                if (line.length >= 13) {
                    processRow(line);
                    count++;
                }
            }
            log.info("Sincronizacao concluida. {} focos processados.", count);
        } catch (Exception e) {
            log.error("Erro ao processar CSV", e);
        }
    }

    private void processRow(String[] line) {
        // FIRMS VIIRS Format:
        // latitude,longitude,bright_ti4,scan,track,acq_date,acq_time,satellite,instrument,confidence,version,bright_ti5,frp,daynight
        try {
            double lat = Double.parseDouble(line[0]);
            double lon = Double.parseDouble(line[1]);
            String acqDateStr = line[5];
            String acqTimeStr = line[6];
            String satellite = line[7];
            String confidence = line[8];
            double frp = Double.parseDouble(line[11]);

            // External ID strategy: satellite + date + time + lat + lon (approx)
            String externalId = String.format("%s-%s-%s-%.2f-%.2f", satellite, acqDateStr, acqTimeStr, lat, lon);

            Optional<FireEvent> existing = fireEventRepository.findByExternalId(externalId);
            if (existing.isEmpty()) {
                FireEvent event = new FireEvent();
                event.setExternalId(externalId);
                event.setSatellite(satellite);
                event.setConfidence(confidence);
                event.setFrp(frp);

                LocalDate date = LocalDate.parse(acqDateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                // Time is like 1420 (HHmm)
                String hour = acqTimeStr.length() >= 4 ? acqTimeStr.substring(0, 2) : "00";
                String minute = acqTimeStr.length() >= 4 ? acqTimeStr.substring(2, 4) : "00";
                LocalTime time = LocalTime.of(Integer.parseInt(hour), Integer.parseInt(minute));
                event.setAcquisitionDate(LocalDateTime.of(date, time));

                Point point = geometryFactory.createPoint(new Coordinate(lon, lat));
                event.setGeom(point);

                fireEventRepository.save(event);

                // Check for critical fires (e.g. FRP > 100)
                if (frp > 100.0) {
                    log.info("Foco critico detectado! FRP: {}", frp);
                    rabbitTemplate.convertAndSend(RabbitMQConfig.FIRE_EVENTS_EXCHANGE, RabbitMQConfig.ROUTING_KEY_CRITICAL, event.getId().toString());
                }
            }
        } catch (Exception e) {
            log.warn("Erro ao processar linha do CSV", e);
        }
    }
}
