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
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.StringReader;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import java.io.InputStream;
import org.locationtech.jts.geom.Geometry;

@Service
@RequiredArgsConstructor
@Slf4j
public class NasaIngestionService implements org.springframework.boot.CommandLineRunner {

    private final FocoCalorRepository focoCalorRepository;
    private final ClusterizacaoService clusterizacaoService;
    private final GravidadeScoreService gravidadeScoreService;
    private final RabbitTemplate rabbitTemplate;

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

    private Geometry brazilGeometry;

    private void carregarGeometriaBrasil() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            InputStream is = new ClassPathResource("brazil.json").getInputStream();
            JsonNode root = mapper.readTree(is);
            JsonNode coordinates = root.path("features").get(0).path("geometry").path("coordinates").get(0);
            
            Coordinate[] coords = new Coordinate[coordinates.size()];
            for (int i = 0; i < coordinates.size(); i++) {
                JsonNode point = coordinates.get(i);
                double lon = point.get(0).asDouble();
                double lat = point.get(1).asDouble();
                coords[i] = new Coordinate(lon, lat);
            }
            brazilGeometry = geometryFactory.createPolygon(coords);
            log.info("Geometria do Brasil carregada com sucesso ({} pontos).", coords.length);
        } catch (Exception e) {
            log.error("Erro ao carregar geometria do Brasil", e);
        }
    }

    @Override
    public void run(String... args) throws Exception {
        carregarGeometriaBrasil();
        long quantidade = focoCalorRepository.count();
        if (quantidade == 0) {
            log.info("Banco de dados vazio (0 focos). Executando carga inicial de dados (7 dias)...");
            sincronizarDadosNasa("7d");
        } else {
            log.info("Banco de dados já contém {} focos. Carga inicial ignorada. Sincronização ocorrerá via agendamento.", quantidade);
        }
    }

    @Scheduled(cron = "${app.sync.cron}")
    public void sincronizarDadosAgendado() {
        log.info("Executando sincronização agendada (24h)...");
        sincronizarDadosNasa("24h");
    }

    public void sincronizarDadosNasa(String periodo) {
        log.info("Iniciando busca de dados da NASA FIRMS (período: {})...", periodo);
        try {
            RestTemplate restTemplate = new RestTemplate();
            
            String fullUrl;
            if (nasaUrl.contains("YOUR_NASA_FIRMS_MAP_KEY") || "YOUR_NASA_FIRMS_MAP_KEY".equals(mapKey)) {
                log.info("Nasa Map Key não configurada. Utilizando base pública da América do Sul.");
                if ("7d".equals(periodo)) {
                    fullUrl = "https://firms.modaps.eosdis.nasa.gov/data/active_fire/noaa-20-viirs-c2/csv/J1_VIIRS_C2_South_America_7d.csv";
                } else {
                    fullUrl = "https://firms.modaps.eosdis.nasa.gov/data/active_fire/noaa-20-viirs-c2/csv/J1_VIIRS_C2_South_America_24h.csv";
                }
            } else {
                // A URL real do FIRMS usa a Map Key.
                fullUrl = nasaUrl.replace("YOUR_NASA_FIRMS_MAP_KEY", mapKey);
                // Aqui seria necessário adaptar a URL com a mapKey para lidar com 7d/24h se for a API dinâmica
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
            if (headers == null) return;

            Map<String, Integer> colMap = new HashMap<>();
            for (int i = 0; i < headers.length; i++) {
                colMap.put(headers[i].trim().toLowerCase(), i);
            }

            String[] record;
            while ((record = csvReader.readNext()) != null) {
                try {
                    double lat = Double.parseDouble(record[colMap.get("latitude")]);
                    double lon = Double.parseDouble(record[colMap.get("longitude")]);
                    String confidence = colMap.containsKey("confidence") ? record[colMap.get("confidence")] : "nominal";
                    double frp = colMap.containsKey("frp") ? Double.parseDouble(record[colMap.get("frp")]) : 0.0;

                    // 1. Filtro de Ingestão: Qualidade
                    if ("low".equalsIgnoreCase(confidence) || "l".equalsIgnoreCase(confidence) || isConfidenceTooLow(confidence)) {
                        continue; // Ignora falsos positivos
                    }

                    // 2. Filtro de Ingestão: Espacial (Apenas Brasil)
                    if (lat < LAT_MIN || lat > LAT_MAX || lon < LON_MIN || lon > LON_MAX) {
                        continue; // Fora do Brasil (Bounding Box Rápido)
                    }

                    if (brazilGeometry != null) {
                        Point pt = geometryFactory.createPoint(new Coordinate(lon, lat));
                        if (!brazilGeometry.contains(pt)) {
                            continue; // Excluir países de fora do Brasil
                        }
                    }

                    String acqDate = record[colMap.get("acq_date")];
                    String acqTime = record[colMap.get("acq_time")]; // Ex: "1430" (14:30)
                    String satellite = colMap.containsKey("satellite") ? record[colMap.get("satellite")] : "N/A";
                    String instrument = colMap.containsKey("instrument") ? record[colMap.get("instrument")] : "VIIRS";

                    String externalId = gerarExternalId(satellite, acqDate, acqTime, lat, lon);

                    // Evita duplicidade se já ingeriu
                    if (focoCalorRepository.existsByExternalId(externalId)) {
                        continue;
                    }

                    FocoCalor foco = parseToFocoCalor(lat, lon, acqDate, acqTime, satellite, instrument, confidence, frp, externalId);
                    
                    // 3. Clusterização
                    var eventoAlvo = clusterizacaoService.processarFoco(foco);

                    // 4. Score de Gravidade e Mensageria
                    if (eventoAlvo != null) {
                        gravidadeScoreService.avaliarGravidade(eventoAlvo);
                        try {
                            rabbitTemplate.convertAndSend("fortivus.exchange", "fire_event.updated", eventoAlvo.getId().toString());
                        } catch (Exception e) {
                            log.warn("Falha ao publicar evento no RabbitMQ: {}", e.getMessage());
                        }
                    }
                } catch (Exception ex) {
                    log.warn("Falha ao parsear linha do CSV: {}", ex.getMessage());
                }
            }

            log.info("Processamento do CSV da NASA concluído com sucesso.");
        } catch (Exception e) {
            log.error("Falha ao ler CSV", e);
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
