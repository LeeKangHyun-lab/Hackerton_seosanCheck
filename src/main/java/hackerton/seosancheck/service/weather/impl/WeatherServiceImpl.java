package hackerton.seosancheck.service.weather.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import hackerton.seosancheck.model.weather.DailyForecast;
import hackerton.seosancheck.model.weather.WeatherDTO;
import hackerton.seosancheck.service.weather.WeatherService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
@RequiredArgsConstructor
public class WeatherServiceImpl implements WeatherService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${weather.api.key}")
    private String apiKey;

    private static final String API_URL = "http://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getVilageFcst";
    private static final String SEOSAN_NX = "48";
    private static final String SEOSAN_NY = "109";

    @Override
    public WeatherDTO getSeosanWeather() {
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();
        String baseDate = today.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String baseTime = getBaseTime(now);

        if ("2300".equals(baseTime) && now.getHour() < 2) {
            baseDate = today.minusDays(1).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        }

        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(API_URL)
                .queryParam("serviceKey", apiKey)
                .queryParam("pageNo", "1")
                .queryParam("numOfRows", "1000")
                .queryParam("dataType", "JSON")
                .queryParam("base_date", baseDate)
                .queryParam("base_time", baseTime)
                .queryParam("nx", SEOSAN_NX)
                .queryParam("ny", SEOSAN_NY);

        URI uri = builder.build(true).toUri();

        System.out.println("Request URL: " + uri);

        try {
            String response = restTemplate.getForObject(uri, String.class);
            return parseWeatherResponse(response);
        } catch (Exception e) {
            e.printStackTrace();
            return new WeatherDTO("정보 없음", "정보 없음", "정보 없음", new ArrayList<>());
        }
    }

    private WeatherDTO parseWeatherResponse(String jsonResponse) throws Exception {
        JsonNode root = objectMapper.readTree(jsonResponse);
        JsonNode items = root.path("response").path("body").path("items").path("item");

        if (!items.isArray()) {
            if (root.path("response").path("header").has("resultMsg")) {
                String errorMsg = root.path("response").path("header").path("resultMsg").asText();
                System.out.println("기상청 API 오류: " + errorMsg);
            }
            return new WeatherDTO("정보 없음", "정보 없음", "정보 없음", new ArrayList<>());
        }

        String currentTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HH00"));
        String todayDate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        String currentTemp = findCurrentValue(items, todayDate, currentTime, "T1H");
        if ("정보 없음".equals(currentTemp)) {
            currentTemp = findCurrentValue(items, todayDate, currentTime, "TMP");
        }

        String currentSkyCode = findCurrentValue(items, todayDate, currentTime, "SKY");
        String currentPtyCode = findCurrentValue(items, todayDate, currentTime, "PTY");

        Map<String, DailyForecast> forecastMap = StreamSupport.stream(items.spliterator(), false)
                .collect(Collectors.groupingBy(
                        item -> item.get("fcstDate").asText(),
                        Collectors.collectingAndThen(Collectors.toList(), list -> {
                            DailyForecast forecast = new DailyForecast();
                            forecast.setDate(list.get(0).get("fcstDate").asText());

                            // --- null 값 처리를 위한 로직 개선 ---
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

                            // 최고/최저 기온이 null일 경우, 시간별 기온(TMP)에서 찾아서 채움
                            if (forecast.getTempMax() == null) {
                                findDailyExtremeTemp(list, "TMP").ifPresent(temp -> forecast.setTempMax(String.valueOf(temp)));
                            }
                            if (forecast.getTempMin() == null) {
                                findDailyExtremeTemp(list, "TMP").ifPresent(temp -> forecast.setTempMin(String.valueOf(temp)));
                            }

                            // 오전/오후 하늘 정보가 null일 경우 서로의 값으로 채움
                            if (forecast.getSkyAm() == null) forecast.setSkyAm(forecast.getSkyPm());
                            if (forecast.getSkyPm() == null) forecast.setSkyPm(forecast.getSkyAm());
                            // ------------------------------------

                            return forecast;
                        })
                ));

        List<DailyForecast> weeklyForecast = new ArrayList<>(forecastMap.values());
        weeklyForecast.sort(Comparator.comparing(DailyForecast::getDate));

        return new WeatherDTO(
                currentTemp,
                getSkyStatus(currentSkyCode),
                getPrecipitationStatus(currentPtyCode),
                weeklyForecast
        );
    }

    private OptionalDouble findDailyExtremeTemp(List<JsonNode> dailyItems, String category) {
        return dailyItems.stream()
                .filter(item -> category.equals(item.get("category").asText()))
                .mapToDouble(item -> Double.parseDouble(item.get("fcstValue").asText()))
                .max(); // TMX를 찾을 때, min()은 TMN을 찾을 때 사용
    }

    private String findCurrentValue(JsonNode items, String date, String time, String category) {
        return StreamSupport.stream(items.spliterator(), false)
                .filter(item -> item.get("fcstDate").asText().equals(date) &&
                        item.get("category").asText().equals(category))
                .min(Comparator.comparingInt(item -> Math.abs(Integer.parseInt(item.get("fcstTime").asText()) - Integer.parseInt(time))))
                .map(item -> item.get("fcstValue").asText())
                .orElse("정보 없음");
    }

    private String getBaseTime(LocalTime now) {
        int hour = now.getHour();
        int minute = now.getMinute();

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

    private String getSkyStatus(String skyCode) {
        if (skyCode == null) return "정보 없음";
        switch (skyCode) {
            case "1": return "맑음";
            case "3": return "구름많음";
            case "4": return "흐림";
            default: return "정보 없음";
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
}