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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
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
import java.util.Optional;
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

    // 💡 JPA와 MyBatis의 트랜잭션 충돌을 해결하기 위해 트랜잭션 관리자를 직접 주입받습니다.
    private final PlatformTransactionManager transactionManager;

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
    public WeatherDTO getSeosanWeather() {
        LocalDate today = LocalDate.now();
        // @Transactional 어노테이션 대신 수동으로 트랜잭션을 관리하므로, 읽기 작업도 트랜잭션 내에서 수행합니다.
        DefaultTransactionDefinition def = new DefaultTransactionDefinition();
        TransactionStatus status = transactionManager.getTransaction(def);
        List<DailyWeatherCache> cachedForecasts;
        try {
            cachedForecasts = weatherCacheRepository.findByForecastDateBetween(today, today.plusDays(7));
            transactionManager.commit(status);
        } catch (Exception e) {
            transactionManager.rollback(status);
            throw e; // 오류 발생 시 예외를 던져서 알림
        }

        List<DailyForecast> weeklyForecast;

        if (cachedForecasts.isEmpty() || cachedForecasts.get(0).getFetchedAt().toLocalDate().isBefore(today)) {
            weeklyForecast = fetchAndCacheNewForecasts(today);
        } else {
            System.out.println("✅ Valid cache found in DB. Using cached data.");
            weeklyForecast = cachedForecasts.stream()
                    .map(this::convertCacheToDto)
                    .sorted(Comparator.comparing(DailyForecast::getDate))
                    .collect(Collectors.toList());
        }

        WeatherDTO realtimeWeather = getRealtimeWeather();

        DailyForecast todayForecast = weeklyForecast.stream()
                .filter(forecast -> LocalDate.parse(forecast.getDate(), DateTimeFormatter.ofPattern("yyyyMMdd")).isEqual(today))
                .findFirst()
                .orElse(new DailyForecast());

        List<DailyForecast> next7DaysForecast = weeklyForecast.stream()
                .filter(forecast -> LocalDate.parse(forecast.getDate(), DateTimeFormatter.ofPattern("yyyyMMdd")).isAfter(today))
                .limit(7)
                .collect(Collectors.toList());

        return new WeatherDTO(
                realtimeWeather.getCurrentTemperature(),
                todayForecast.getSkyPm(),
                realtimeWeather.getPrecipitation(),
                todayForecast.getTempMax(),
                todayForecast.getTempMin(),
                next7DaysForecast
        );
    }

    private List<DailyForecast> fetchAndCacheNewForecasts(LocalDate today) {
        // 💡 트랜잭션을 수동으로 시작하여 DB 저장 실패를 방지합니다.
        TransactionStatus status = transactionManager.getTransaction(new DefaultTransactionDefinition());
        try {
            System.out.println("--- Starting new forecast fetch ---");

            WeatherDTO shortTermData = getShortTermForecast();
            int shortTermSize = shortTermData.getWeeklyForecast() != null ? shortTermData.getWeeklyForecast().size() : 0;
            System.out.println("✅ Step 1: Fetched " + shortTermSize + " days from short-term forecast.");

            List<DailyForecast> midTermData = getMidTermForecast(today);
            System.out.println("✅ Step 2: Fetched " + midTermData.size() + " days from mid-term forecast.");

            Map<String, DailyForecast> forecastMap = shortTermData.getWeeklyForecast().stream()
                    .collect(Collectors.toMap(DailyForecast::getDate, f -> f));
            midTermData.forEach(mid -> forecastMap.putIfAbsent(mid.getDate(), mid));
            System.out.println("✅ Step 3: Combined forecasts. Total unique days: " + forecastMap.size());

            List<DailyForecast> finalForecast = new ArrayList<>(forecastMap.values());
            finalForecast.sort(Comparator.comparing(DailyForecast::getDate));

            if (!finalForecast.isEmpty()) {
                System.out.println("✅ Step 4: Preparing to save " + finalForecast.size() + " days to the database.");
                weatherCacheRepository.deleteAllByForecastDateBefore(today);
                List<DailyWeatherCache> newCache = finalForecast.stream()
                        .map(this::convertDtoToCache)
                        .collect(Collectors.toList());
                weatherCacheRepository.saveAll(newCache);
                System.out.println("✅ Step 5: Save operation completed.");
            }

            // 💡 모든 작업이 성공하면 트랜잭션을 커밋(최종 저장)합니다.
            transactionManager.commit(status);
            System.out.println("--- ✅ Transaction committed successfully! ---");
            return finalForecast;

        } catch (Exception e) {
            // 💡 오류 발생 시 모든 작업을 취소(롤백)합니다.
            transactionManager.rollback(status);
            System.err.println("--- ❌ An error occurred! Transaction has been rolled back. ---");
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    // ... (이하 다른 메서드들은 이전과 동일합니다) ...

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

    private WeatherDTO parseShortTermResponse(String jsonResponse) throws Exception {
        JsonNode root = objectMapper.readTree(jsonResponse);
        JsonNode items = root.path("response").path("body").path("items").path("item");

        if (!items.isArray()) {
            return new WeatherDTO();
        }

        Map<String, List<JsonNode>> dailyItemsMap = StreamSupport.stream(items.spliterator(), false)
                .collect(Collectors.groupingBy(item -> item.get("fcstDate").asText()));

        List<DailyForecast> weeklyForecast = dailyItemsMap.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    String date = entry.getKey();
                    List<JsonNode> dailyItems = entry.getValue();

                    String tempMax = findCategoryValue(dailyItems, "TMX").orElse(null);
                    String tempMin = findCategoryValue(dailyItems, "TMN").orElse(null);

                    if (tempMax == null) {
                        OptionalDouble maxTempOpt = findDailyExtremeTemp(dailyItems, true);
                        tempMax = maxTempOpt.isPresent() ? String.format("%.1f", maxTempOpt.getAsDouble()) : "정보 없음";
                    }
                    if (tempMin == null) {
                        OptionalDouble minTempOpt = findDailyExtremeTemp(dailyItems, false);
                        tempMin = minTempOpt.isPresent() ? String.format("%.1f", minTempOpt.getAsDouble()) : "정보 없음";
                    }

                    String skyAm = findSkyValue(dailyItems, true).orElse("정보 없음");
                    String skyPm = findSkyValue(dailyItems, false).orElse("정보 없음");

                    return new DailyForecast(date, tempMax, tempMin, skyAm, skyPm);
                })
                .collect(Collectors.toList());

        WeatherDTO result = new WeatherDTO();
        result.setWeeklyForecast(weeklyForecast);
        return result;
    }

    private Optional<String> findCategoryValue(List<JsonNode> items, String category) {
        return items.stream()
                .filter(item -> category.equals(item.get("category").asText()))
                .map(item -> item.get("fcstValue").asText())
                .findFirst();
    }

    private OptionalDouble findDailyExtremeTemp(List<JsonNode> dailyItems, boolean findMax) {
        Stream<JsonNode> tempItems = dailyItems.stream().filter(item -> "TMP".equals(item.get("category").asText()));
        if (findMax) {
            return tempItems.mapToDouble(item -> Double.parseDouble(item.get("fcstValue").asText())).max();
        } else {
            return tempItems.mapToDouble(item -> Double.parseDouble(item.get("fcstValue").asText())).min();
        }
    }

    private Optional<String> findSkyValue(List<JsonNode> items, boolean isAm) {
        int startTime = isAm ? 600 : 1500;
        int endTime = isAm ? 1200 : 2100;

        return items.stream()
                .filter(item -> "SKY".equals(item.get("category").asText()))
                .filter(item -> {
                    int time = Integer.parseInt(item.get("fcstTime").asText());
                    return time >= startTime && time <= endTime;
                })
                .map(item -> getSkyStatus(item.get("fcstValue").asText()))
                .findFirst();
    }

    private List<DailyForecast> getMidTermForecast(LocalDate today) {
        try {
            String tmFc = getMidTermBaseTime();
            String tempUrl = buildMidTermUrl(midTermTempUrl, tmFc);
            String landUrl = buildMidTermUrl(midTermLandUrl, tmFc);
            String tempResponse = restTemplate.getForObject(tempUrl, String.class);
            MidTermTempDTO tempDTO = parseMidTermResponse(tempResponse, MidTermTempDTO.class);
            String landResponse = restTemplate.getForObject(landUrl, String.class);
            MidTermLandDTO landDTO = parseMidTermResponse(landResponse, MidTermLandDTO.class);
            return combineMidTermForecasts(tempDTO, landDTO, today);
        } catch (Exception e) {
            System.err.println("❌ Mid-term forecast API call failed: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    private String getFieldValue(Object dto, String fieldName) {
        try {
            return String.valueOf(dto.getClass().getDeclaredField(fieldName).get(dto));
        } catch (Exception e) {
            return null;
        }
    }

    private List<DailyForecast> combineMidTermForecasts(MidTermTempDTO temp, MidTermLandDTO land, LocalDate today) {
        List<DailyForecast> forecasts = new ArrayList<>();
        for (int i = 3; i <= 10; i++) {
            String date = today.plusDays(i).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String tempMinStr = getFieldValue(temp, "taMin" + i);
            String tempMaxStr = getFieldValue(temp, "taMax" + i);

            if (tempMinStr == null || tempMaxStr == null) {
                continue;
            }

            String skyAm;
            String skyPm;

            if (i <= 7) {
                skyAm = getFieldValue(land, "wf" + i + "Am");
                skyPm = getFieldValue(land, "wf" + i + "Pm");
            } else {
                String sky = getFieldValue(land, "wf" + i);
                skyAm = sky;
                skyPm = sky;
            }

            forecasts.add(new DailyForecast(
                    date,
                    tempMaxStr,
                    tempMinStr,
                    skyAm != null ? skyAm : "정보 없음",
                    skyPm != null ? skyPm : "정보 없음"
            ));
        }
        return forecasts;
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
            System.err.println("❌ Realtime weather API call failed: " + e.getMessage());
            return new WeatherDTO();
        }
    }

    private WeatherDTO parseRealtimeResponse(String jsonResponse) throws Exception {
        JsonNode root = objectMapper.readTree(jsonResponse);
        JsonNode items = root.path("response").path("body").path("items").path("item");

        WeatherDTO dto = new WeatherDTO();
        if (!items.isArray() || items.size() == 0) {
            return dto;
        }

        Map<String, String> realtimeData = StreamSupport.stream(items.spliterator(), false)
                .collect(Collectors.toMap(
                        item -> item.get("category").asText(),
                        item -> item.get("obsrValue").asText(),
                        (existing, replacement) -> existing
                ));

        dto.setCurrentTemperature(realtimeData.getOrDefault("T1H", "정보 없음"));
        dto.setPrecipitation(getPrecipitationStatus(realtimeData.getOrDefault("PTY", "정보 없음")));
        return dto;
    }

    private String buildMidTermUrl(String baseUrl, String tmFc) {
        return UriComponentsBuilder.fromHttpUrl(baseUrl)
                .queryParam("serviceKey", apiKey).queryParam("pageNo", "1").queryParam("numOfRows", "10")
                .queryParam("dataType", "JSON").queryParam("regId", seosanRegionId).queryParam("tmFc", tmFc)
                .build(true).toUriString();
    }

    private <T> T parseMidTermResponse(String jsonResponse, Class<T> dtoClass) throws Exception {
        JsonNode root = objectMapper.readTree(jsonResponse);
        JsonNode itemNode = root.path("response").path("body").path("items").path("item").get(0);
        return objectMapper.treeToValue(itemNode, dtoClass);
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
        return DailyWeatherCache.builder()
                .forecastDate(forecastDate)
                .fetchedAt(LocalDateTime.now())
                .tempMax(dto.getTempMax())
                .tempMin(dto.getTempMin())
                .skyAm(dto.getSkyAm())
                .skyPm(dto.getSkyPm())
                .build();
    }

    private DailyForecast convertCacheToDto(DailyWeatherCache cache) {
        return new DailyForecast(
                cache.getForecastDate().format(DateTimeFormatter.ofPattern("yyyyMMdd")),
                cache.getTempMax(),
                cache.getTempMin(),
                cache.getSkyAm(),
                cache.getSkyPm()
        );
    }
}
