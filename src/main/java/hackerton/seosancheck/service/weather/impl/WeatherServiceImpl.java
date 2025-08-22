package hackerton.seosancheck.service.weather.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import hackerton.seosancheck.entity.DailyWeatherCache;
import hackerton.seosancheck.model.weather.DailyForecast;
import hackerton.seosancheck.model.weather.WeatherDTO;
import hackerton.seosancheck.repository.DailyWeatherCacheRepository;
import hackerton.seosancheck.service.weather.WeatherService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
@RequiredArgsConstructor
public class WeatherServiceImpl implements WeatherService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final DailyWeatherCacheRepository weatherCacheRepository;

    @Value("${weather.api.key}")
    private String apiKey;

    private static final String REALTIME_API_URL = "http://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getUltraSrtNcst";
    private static final String SHORT_TERM_API_URL = "http://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getVilageFcst";
    private static final String SEOSAN_NX = "48";
    private static final String SEOSAN_NY = "109";

    @Override
    @Transactional
    public WeatherDTO getSeosanWeather() {
        LocalDate today = LocalDate.now();
        List<DailyWeatherCache> cachedEntities = weatherCacheRepository.findByForecastDateBetween(today, today.plusDays(2));
        List<DailyForecast> forecasts;

        // 실시간 날씨 정보는 항상 새로 조회
        WeatherDTO realtimeWeather = fetchRealtimeWeather();

        if (cachedEntities.isEmpty() || cachedEntities.get(0).getFetchedAt().toLocalDate().isBefore(today)) {
            // 캐시가 없거나 낡았으면, API를 새로 호출하고 DB에 저장
            forecasts = fetchAndCacheShortTermForecast(today);
        } else {
            // 유효한 캐시가 있으면 DB 데이터 사용
            forecasts = cachedEntities.stream()
                    .map(this::convertCacheToDto)
                    .sorted(Comparator.comparing(DailyForecast::getDate))
                    .collect(Collectors.toList());

            // 실시간 온도를 기반으로 캐시를 업데이트하는 로직 호출
            updateCacheWithRealtimeTemp(today, forecasts, realtimeWeather.getCurrentTemperature());
        }

        DailyForecast todayForecast = forecasts.stream()
                .filter(f -> f.getDate().equals(today.format(DateTimeFormatter.ofPattern("yyyyMMdd"))))
                .findFirst()
                .orElse(new DailyForecast());

        List<DailyForecast> weeklyForecast = forecasts.stream()
                .filter(f -> LocalDate.parse(f.getDate(), DateTimeFormatter.ofPattern("yyyyMMdd")).isAfter(today))
                .collect(Collectors.toList());

        return new WeatherDTO(
                realtimeWeather.getCurrentTemperature(),
                realtimeWeather.getCurrentSky(),
                realtimeWeather.getPrecipitation(),
                todayForecast.getTempMax(),
                todayForecast.getTempMin(),
                weeklyForecast
        );
    }

    private void updateCacheWithRealtimeTemp(LocalDate today, List<DailyForecast> forecasts, String currentTempStr) {
        try {
            DailyForecast todayForecast = forecasts.stream()
                    .filter(f -> f.getDate().equals(today.format(DateTimeFormatter.ofPattern("yyyyMMdd"))))
                    .findFirst()
                    .orElse(null);

            if (todayForecast == null || "정보 없음".equals(currentTempStr) || currentTempStr == null) {
                return;
            }

            double currentTemp = Double.parseDouble(currentTempStr);
            double cachedMaxTemp = Double.parseDouble(todayForecast.getTempMax());
            double cachedMinTemp = Double.parseDouble(todayForecast.getTempMin());
            boolean isUpdated = false;

            if (currentTemp > cachedMaxTemp) {
                todayForecast.setTempMax(String.valueOf(currentTemp));
                isUpdated = true;
            }
            if (currentTemp < cachedMinTemp) {
                todayForecast.setTempMin(String.valueOf(currentTemp));
                isUpdated = true;
            }

            if (isUpdated) {
                weatherCacheRepository.findById(today).ifPresent(cache -> {
                    cache.setTempMax(todayForecast.getTempMax());
                    cache.setTempMin(todayForecast.getTempMin());
                    weatherCacheRepository.save(cache);
                });
            }
        } catch (NumberFormatException e) {
            e.printStackTrace();
        }
    }

    @Transactional
    public List<DailyForecast> fetchAndCacheShortTermForecast(LocalDate today) {
        try {
            List<DailyForecast> newForecasts = fetchShortTermForecastApi();
            weatherCacheRepository.deleteAllByForecastDateBefore(today);
            List<DailyWeatherCache> newCaches = newForecasts.stream()
                    .map(this::convertDtoToCache)
                    .collect(Collectors.toList());
            weatherCacheRepository.saveAll(newCaches);
            return newForecasts;
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    private WeatherDTO fetchRealtimeWeather() {
        try {
            LocalTime now = LocalTime.now();
            String baseDate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String baseTime = getRealtimeBaseTime(now);

            if (now.getHour() == 0 && now.getMinute() < 45) {
                baseDate = LocalDate.now().minusDays(1).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            }

            URI uri = buildApiUri(REALTIME_API_URL, baseDate, baseTime, "100");
            String response = restTemplate.getForObject(uri, String.class);
            return parseRealtimeResponse(response);
        } catch (Exception e) {
            e.printStackTrace();
            return new WeatherDTO();
        }
    }

    private List<DailyForecast> fetchShortTermForecastApi() throws Exception {
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();
        String baseDate = today.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String baseTime = getShortTermBaseTime(now);

        if (now.getHour() < 2 || (now.getHour() == 2 && now.getMinute() < 15)) {
            baseDate = today.minusDays(1).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        }

        URI uri = buildApiUri(SHORT_TERM_API_URL, baseDate, baseTime, "1000");
        String response = restTemplate.getForObject(uri, String.class);
        return parseShortTermResponse(response);
    }

    private URI buildApiUri(String baseUrl, String baseDate, String baseTime, String numOfRows) {
        return UriComponentsBuilder.fromHttpUrl(baseUrl)
                .queryParam("serviceKey", apiKey)
                .queryParam("pageNo", "1")
                .queryParam("numOfRows", numOfRows)
                .queryParam("dataType", "JSON")
                .queryParam("base_date", baseDate)
                .queryParam("base_time", baseTime)
                .queryParam("nx", SEOSAN_NX)
                .queryParam("ny", SEOSAN_NY)
                .build(true).toUri();
    }

    private WeatherDTO parseRealtimeResponse(String jsonResponse) throws Exception {
        JsonNode items = getItemsNode(jsonResponse);
        WeatherDTO dto = new WeatherDTO();
        if (items == null) return dto;

        Map<String, String> realtimeData = StreamSupport.stream(items.spliterator(), false)
                .collect(Collectors.toMap(
                        item -> item.get("category").asText(),
                        item -> item.get("obsrValue").asText()
                ));

        dto.setCurrentTemperature(realtimeData.getOrDefault("T1H", "정보 없음"));
        dto.setPrecipitation(getPrecipitationStatus(realtimeData.getOrDefault("PTY", "0")));
        dto.setCurrentSky(getSkyStatus(realtimeData.getOrDefault("SKY", "1")));
        return dto;
    }

    private List<DailyForecast> parseShortTermResponse(String jsonResponse) throws Exception {
        JsonNode items = getItemsNode(jsonResponse);
        if (items == null) return new ArrayList<>();

        Map<String, List<JsonNode>> dailyItemsMap = StreamSupport.stream(items.spliterator(), false)
                .collect(Collectors.groupingBy(item -> item.get("fcstDate").asText()));

        return dailyItemsMap.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    String date = entry.getKey();
                    List<JsonNode> dailyItems = entry.getValue();

                    String tempMax = findValue(dailyItems, "TMX")
                            .orElseGet(() -> findExtremeFromHourly(dailyItems, true));
                    String tempMin = findValue(dailyItems, "TMN")
                            .orElseGet(() -> findExtremeFromHourly(dailyItems, false));

                    String skyAm = findSkyValue(dailyItems, true);
                    String skyPm = findSkyValue(dailyItems, false);

                    DailyForecast forecast = new DailyForecast();
                    forecast.setDate(date);
                    forecast.setTempMax(tempMax);
                    forecast.setTempMin(tempMin);
                    forecast.setSkyAm(skyAm);
                    forecast.setSkyPm(skyPm);
                    return forecast;
                })
                .collect(Collectors.toList());
    }

    private JsonNode getItemsNode(String jsonResponse) throws Exception {
        JsonNode root = objectMapper.readTree(jsonResponse);
        JsonNode items = root.path("response").path("body").path("items").path("item");
        if (items.isMissingNode() || !items.isArray()) {
            System.err.println("API Error: " + root.path("response").path("header").path("resultMsg").asText("No items found"));
            return null;
        }
        return items;
    }

    private Optional<String> findValue(List<JsonNode> items, String category) {
        return items.stream()
                .filter(item -> category.equals(item.get("category").asText()))
                .map(item -> item.get("fcstValue").asText())
                .findFirst();
    }

    private String findExtremeFromHourly(List<JsonNode> items, boolean findMax) {
        OptionalDouble temp = items.stream()
                .filter(item -> "TMP".equals(item.get("category").asText()))
                .mapToDouble(item -> Double.parseDouble(item.get("fcstValue").asText()))
                .reduce((a, b) -> findMax ? Math.max(a, b) : Math.min(a, b));
        return temp.isPresent() ? String.format("%.1f", temp.getAsDouble()) : "정보 없음";
    }

    private String findSkyValue(List<JsonNode> items, boolean isAm) {
        String time = isAm ? "0900" : "1500";
        return items.stream()
                .filter(item -> "SKY".equals(item.get("category").asText()) && time.equals(item.get("fcstTime").asText()))
                .map(item -> getSkyStatus(item.get("fcstValue").asText()))
                .findFirst()
                .orElse("정보 없음");
    }

    private String getRealtimeBaseTime(LocalTime now) {
        int hour = now.getHour();
        if (now.getMinute() < 45) {
            hour = now.minusHours(1).getHour();
        }
        return String.format("%02d00", hour);
    }

    private String getShortTermBaseTime(LocalTime now) {
        int hour = now.getHour();
        if (hour < 2) return "2300";
        if (hour < 5) return "0200";
        if (hour < 8) return "0500";
        if (hour < 11) return "0800";
        if (hour < 14) return "1100";
        if (hour < 17) return "1400";
        if (hour < 20) return "1700";
        if (hour < 23) return "2000";
        return "2300";
    }

    private String getSkyStatus(String skyCode) {
        switch (skyCode) {
            case "1": return "맑음";
            case "3": return "구름많음";
            case "4": return "흐림";
            default: return "정보 없음";
        }
    }

    private String getPrecipitationStatus(String ptyCode) {
        switch (ptyCode) {
            case "0": return "없음";
            case "1": return "비";
            case "2": return "비/눈";
            case "3": return "눈";
            case "5": return "빗방울";
            case "6": return "빗방울눈날림";
            case "7": return "눈날림";
            default: return "정보 없음";
        }
    }

    private DailyWeatherCache convertDtoToCache(DailyForecast dto) {
        return DailyWeatherCache.builder()
                .forecastDate(LocalDate.parse(dto.getDate(), DateTimeFormatter.ofPattern("yyyyMMdd")))
                .fetchedAt(LocalDateTime.now())
                .tempMax(dto.getTempMax())
                .tempMin(dto.getTempMin())
                .skyAm(dto.getSkyAm())
                .skyPm(dto.getSkyPm())
                .build();
    }

    private DailyForecast convertCacheToDto(DailyWeatherCache cache) {
        DailyForecast dto = new DailyForecast();
        dto.setDate(cache.getForecastDate().format(DateTimeFormatter.ofPattern("yyyyMMdd")));
        dto.setTempMax(cache.getTempMax());
        dto.setTempMin(cache.getTempMin());
        dto.setSkyAm(cache.getSkyAm());
        dto.setSkyPm(cache.getSkyPm());
        return dto;
    }
}
