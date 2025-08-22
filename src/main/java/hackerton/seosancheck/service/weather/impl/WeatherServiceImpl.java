package hackerton.seosancheck.service.weather.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import hackerton.seosancheck.dto.weather.DailyForecast;
import hackerton.seosancheck.dto.weather.MidTermLandDTO;
import hackerton.seosancheck.dto.weather.MidTermTempDTO;
import hackerton.seosancheck.dto.weather.WeatherDTO;
import hackerton.seosancheck.entity.DailyWeatherCache;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@Service
@RequiredArgsConstructor
public class WeatherServiceImpl implements WeatherService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final DailyWeatherCacheRepository weatherCacheRepository;

    @Value("${weather.api.key}")
    private String apiKey;
    @Value("${weather.api.mid-term-land-url}")
    private String midTermLandUrl;
    @Value("${weather.api.mid-term-temp-url}")
    private String midTermTempUrl;
    @Value("${weather.api.seosan-region-id}")
    private String seosanRegionId;

    private static final String REALTIME_API_URL = "http://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getUltraSrtNcst";
    private static final String SHORT_TERM_API_URL = "http://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getVilageFcst";
    private static final String SEOSAN_NX = "48";
    private static final String SEOSAN_NY = "109";

    @Override
    @Transactional
    public WeatherDTO getSeosanWeather() {
        LocalDate today = LocalDate.now();
        List<DailyWeatherCache> cachedForecasts = weatherCacheRepository.findByForecastDateBetween(today, today.plusDays(6));
        List<DailyForecast> weeklyForecast;

        if (cachedForecasts.isEmpty() || cachedForecasts.get(0).getFetchedAt().toLocalDate().isBefore(today)) {
            weeklyForecast = fetchAndCacheNewForecasts();
        } else {
            weeklyForecast = cachedForecasts.stream()
                    .map(this::convertCacheToDto)
                    .sorted(Comparator.comparing(DailyForecast::getDate))
                    .collect(Collectors.toList());
        }

        WeatherDTO realtimeWeather = getRealtimeWeather();

        String currentSky = "정보 없음";
        if (!weeklyForecast.isEmpty() && today.isEqual(LocalDate.parse(weeklyForecast.get(0).getDate(), DateTimeFormatter.ofPattern("yyyyMMdd")))) {
            currentSky = weeklyForecast.get(0).getSkyPm();
        }

        // --- 수정된 부분: 오늘부터 7일치 예보를 반환하도록 변경 ---
        List<DailyForecast> finalWeeklyForecast = weeklyForecast.stream()
                .filter(forecast -> !LocalDate.parse(forecast.getDate(), DateTimeFormatter.ofPattern("yyyyMMdd")).isBefore(today))
                .limit(7)
                .collect(Collectors.toList());
        // ----------------------------------------------------

        return new WeatherDTO(
                realtimeWeather.getCurrentTemperature(),
                currentSky,
                realtimeWeather.getPrecipitation(),
                finalWeeklyForecast
        );
    }

    private List<DailyForecast> fetchAndCacheNewForecasts() {
        try {
            WeatherDTO shortTermData = getShortTermForecast();
            List<DailyForecast> midTermData = getMidTermForecast();

            if (shortTermData.getWeeklyForecast() == null) {
                shortTermData.setWeeklyForecast(new ArrayList<>());
            }

            Map<String, DailyForecast> forecastMap = shortTermData.getWeeklyForecast().stream()
                    .collect(Collectors.toMap(DailyForecast::getDate, f -> f));

            midTermData.forEach(mid -> forecastMap.putIfAbsent(mid.getDate(), mid));

            List<DailyForecast> finalForecast = new ArrayList<>(forecastMap.values());
            finalForecast.sort(Comparator.comparing(DailyForecast::getDate));

            if (!finalForecast.isEmpty()) {
                weatherCacheRepository.deleteAll(); // 기존 캐시 삭제
                List<DailyWeatherCache> newCache = finalForecast.stream()
                        .map(this::convertDtoToCache)
                        .collect(Collectors.toList());
                weatherCacheRepository.saveAll(newCache);
            }

            return finalForecast;
        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    private WeatherDTO getRealtimeWeather() {
        try {
            LocalTime now = LocalTime.now();
            String baseTime = getRealtimeBaseTime(now);
            String baseDate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

            if ("2330".equals(baseTime) && now.getHour() == 0) {
                baseDate = LocalDate.now().minusDays(1).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            }

            URI uri = UriComponentsBuilder.fromHttpUrl(REALTIME_API_URL)
                    .queryParam("serviceKey", apiKey).queryParam("pageNo", "1").queryParam("numOfRows", "100")
                    .queryParam("dataType", "JSON").queryParam("base_date", baseDate).queryParam("base_time", baseTime)
                    .queryParam("nx", SEOSAN_NX).queryParam("ny", SEOSAN_NY)
                    .build(true).toUri();

            String response = restTemplate.getForObject(uri, String.class);
            return parseRealtimeResponse(response);
        } catch (Exception e) {
            System.err.println("실시간 날씨 정보 조회 실패: " + e.getMessage());
            return new WeatherDTO("정보 없음", "정보 없음", "정보 없음", new ArrayList<>());
        }
    }

    private WeatherDTO getShortTermForecast() throws Exception {
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();
        String baseDate = today.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String baseTime = getBaseTime(now);

        if ("2300".equals(baseTime) && now.getHour() < 2) {
            baseDate = today.minusDays(1).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        }

        URI uri = UriComponentsBuilder.fromHttpUrl(SHORT_TERM_API_URL)
                .queryParam("serviceKey", apiKey).queryParam("pageNo", "1").queryParam("numOfRows", "1000")
                .queryParam("dataType", "JSON").queryParam("base_date", baseDate).queryParam("base_time", baseTime)
                .queryParam("nx", SEOSAN_NX).queryParam("ny", SEOSAN_NY)
                .build(true).toUri();

        String response = restTemplate.getForObject(uri, String.class);
        return parseShortTermResponse(response);
    }

    private List<DailyForecast> getMidTermForecast() {
        try {
            String tmFc = getMidTermBaseTime();

            String tempUrl = buildMidTermUrl(midTermTempUrl, tmFc);
            String landUrl = buildMidTermUrl(midTermLandUrl, tmFc);

            String tempResponse = restTemplate.getForObject(tempUrl, String.class);
            MidTermTempDTO tempDTO = parseMidTermResponse(tempResponse, MidTermTempDTO.class);

            String landResponse = restTemplate.getForObject(landUrl, String.class);
            MidTermLandDTO landDTO = parseMidTermResponse(landResponse, MidTermLandDTO.class);

            return combineMidTermForecasts(tempDTO, landDTO);
        } catch (Exception e) {
            System.err.println("중기예보 API 호출 실패: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    private String buildMidTermUrl(String baseUrl, String tmFc) {
        return UriComponentsBuilder.fromHttpUrl(baseUrl)
                .queryParam("serviceKey", apiKey).queryParam("pageNo", "1").queryParam("numOfRows", "10")
                .queryParam("dataType", "JSON").queryParam("regId", seosanRegionId).queryParam("tmFc", tmFc)
                .build(true).toUriString();
    }

    private List<DailyForecast> combineMidTermForecasts(MidTermTempDTO temp, MidTermLandDTO land) {
        List<DailyForecast> forecasts = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = 3; i <= 10; i++) {
            try {
                DailyForecast df = new DailyForecast();
                df.setDate(today.plusDays(i).format(DateTimeFormatter.ofPattern("yyyyMMdd")));

                df.setTempMin(String.valueOf(temp.getClass().getDeclaredField("taMin" + i).get(temp)));
                df.setTempMax(String.valueOf(temp.getClass().getDeclaredField("taMax" + i).get(temp)));

                if (i <= 7) {
                    df.setSkyAm((String) land.getClass().getDeclaredField("wf" + i + "Am").get(land));
                    df.setSkyPm((String) land.getClass().getDeclaredField("wf" + i + "Pm").get(land));
                } else {
                    String sky = (String) land.getClass().getDeclaredField("wf" + i).get(land);
                    df.setSkyAm(sky);
                    df.setSkyPm(sky);
                }
                forecasts.add(df);
            } catch (Exception e) {
                break;
            }
        }
        return forecasts;
    }

    private <T> T parseMidTermResponse(String jsonResponse, Class<T> dtoClass) throws Exception {
        JsonNode root = objectMapper.readTree(jsonResponse);
        JsonNode itemNode = root.path("response").path("body").path("items").path("item").get(0);
        return objectMapper.treeToValue(itemNode, dtoClass);
    }

    private WeatherDTO parseShortTermResponse(String jsonResponse) throws Exception {
        JsonNode root = objectMapper.readTree(jsonResponse);
        JsonNode items = root.path("response").path("body").path("items").path("item");

        if (!items.isArray()) { return new WeatherDTO("정보 없음", "정보 없음", "정보 없음", new ArrayList<>()); }

        String currentTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HH00"));
        String todayDate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        String currentTemp = findCurrentValue(items, todayDate, currentTime, "TMP");
        String currentSkyCode = findCurrentValue(items, todayDate, currentTime, "SKY");
        String currentPtyCode = findCurrentValue(items, todayDate, currentTime, "PTY");

        Map<String, DailyForecast> forecastMap = StreamSupport.stream(items.spliterator(), false)
                .collect(Collectors.groupingBy(item -> item.get("fcstDate").asText(),
                        Collectors.collectingAndThen(Collectors.toList(), list -> {
                            DailyForecast forecast = new DailyForecast();
                            forecast.setDate(list.get(0).get("fcstDate").asText());
                            list.forEach(item -> {
                                String category = item.get("category").asText();
                                String value = item.get("fcstValue").asText();
                                switch (category) {
                                    case "TMX": forecast.setTempMax(value); break;
                                    case "TMN": forecast.setTempMin(value); break;
                                    case "SKY":
                                        int time = Integer.parseInt(item.get("fcstTime").asText());
                                        if (time >= 600 && time < 1800) {
                                            if (forecast.getSkyAm() == null) forecast.setSkyAm(getSkyStatus(value));
                                        } else {
                                            if (forecast.getSkyPm() == null) forecast.setSkyPm(getSkyStatus(value));
                                        }
                                        break;
                                }
                            });
                            if (forecast.getTempMax() == null) {
                                findDailyExtremeTemp(list, true).ifPresent(maxTemp -> forecast.setTempMax(String.format("%.1f", maxTemp)));
                            }
                            if (forecast.getTempMin() == null) {
                                findDailyExtremeTemp(list, false).ifPresent(minTemp -> forecast.setTempMin(String.format("%.1f", minTemp)));
                            }
                            if (forecast.getSkyAm() == null) forecast.setSkyAm(forecast.getSkyPm());
                            if (forecast.getSkyPm() == null) forecast.setSkyPm(forecast.getSkyAm());
                            return forecast;
                        })
                ));

        List<DailyForecast> weeklyForecast = new ArrayList<>(forecastMap.values());
        weeklyForecast.sort(Comparator.comparing(DailyForecast::getDate));

        return new WeatherDTO(currentTemp, getSkyStatus(currentSkyCode), getPrecipitationStatus(currentPtyCode), weeklyForecast);
    }

    private WeatherDTO parseRealtimeResponse(String jsonResponse) throws Exception {
        JsonNode root = objectMapper.readTree(jsonResponse);
        JsonNode items = root.path("response").path("body").path("items").path("item");

        if (!items.isArray() || items.size() == 0) {
            return new WeatherDTO("정보 없음", "정보 없음", "정보 없음", new ArrayList<>());
        }

        Map<String, String> realtimeData = StreamSupport.stream(items.spliterator(), false)
                .collect(Collectors.toMap(
                        item -> item.get("category").asText(),
                        item -> item.get("obsrValue").asText(),
                        (existing, replacement) -> existing
                ));

        String temp = realtimeData.getOrDefault("T1H", "정보 없음");
        String ptyCode = realtimeData.getOrDefault("PTY", "정보 없음");

        return new WeatherDTO(temp, "정보 없음", getPrecipitationStatus(ptyCode), new ArrayList<>());
    }

    private OptionalDouble findDailyExtremeTemp(List<JsonNode> dailyItems, boolean findMax) {
        Stream<JsonNode> tempItems = dailyItems.stream().filter(item -> "TMP".equals(item.get("category").asText()));
        if (findMax) {
            return tempItems.mapToDouble(item -> Double.parseDouble(item.get("fcstValue").asText())).max();
        } else {
            return tempItems.mapToDouble(item -> Double.parseDouble(item.get("fcstValue").asText())).min();
        }
    }

    private String findCurrentValue(JsonNode items, String date, String time, String category) {
        return StreamSupport.stream(items.spliterator(), false)
                .filter(item -> item.get("fcstDate").asText().equals(date) && item.get("category").asText().equals(category))
                .min(Comparator.comparingInt(item -> Math.abs(Integer.parseInt(item.get("fcstTime").asText()) - Integer.parseInt(time))))
                .map(item -> item.get("fcstValue").asText())
                .orElse("정보 없음");
    }

    private String getRealtimeBaseTime(LocalTime now) {
        int hour = now.getHour();
        if (now.getMinute() < 45) {
            hour = now.minusHours(1).getHour();
        }
        return String.format("%02d30", hour);
    }

    private String getBaseTime(LocalTime now) {
        int hour = now.getHour(); int minute = now.getMinute();
        if (hour < 2 || (hour == 2 && minute < 15)) return "2300";
        if (hour < 5 || (hour == 5 && minute < 15)) return "0200";
        if (hour < 8 || (hour == 8 && minute < 15)) return "0500";
        if (hour < 11 || (hour == 11 && minute < 15)) return "0800";
        if (hour < 14 || (hour == 14 && minute < 15)) return "1100";
        if (hour < 17 || (hour == 17 && minute < 15)) return "1400";
        if (hour < 20 || (hour == 20 && minute < 15)) return "1700";
        if (hour < 23 || (hour == 23 && minute < 15)) return "2000";
        return "2300";
    }

    private String getMidTermBaseTime() {
        LocalTime now = LocalTime.now();
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        if (now.getHour() < 6) {
            return LocalDate.now().minusDays(1).format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "1800";
        } else if (now.getHour() < 18) {
            return today + "0600";
        } else {
            return today + "1800";
        }
    }

    private String getSkyStatus(String skyCode) {
        if (skyCode == null) return "정보 없음";
        switch (skyCode) {
            case "1": return "맑음";
            case "3": return "구름많음";
            case "4": return "흐림";
            default: return skyCode;
        }
    }

    private String getPrecipitationStatus(String ptyCode) {
        if (ptyCode == null) return "없음";
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
        LocalDate forecastDate = LocalDate.parse(dto.getDate(), DateTimeFormatter.ofPattern("yyyyMMdd"));
        return new DailyWeatherCache(
                forecastDate,
                LocalDateTime.now(),
                dto.getTempMax(),
                dto.getTempMin(),
                dto.getSkyAm(),
                dto.getSkyPm()
        );
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
