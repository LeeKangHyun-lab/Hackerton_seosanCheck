package hackerton.seosancheck.service.weather.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import hackerton.seosancheck.dto.weather.DailyForecast;
import hackerton.seosancheck.dto.weather.WeatherDTO;
import hackerton.seosancheck.service.weather.WeatherService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder; // import 문 추가

import java.net.URI; // java.net.URI를 import 합니다.
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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

        if (now.getHour() < 2) {
            baseDate = today.minusDays(1).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        }

        // --- 수정된 부분: UriComponentsBuilder를 사용하여 안전한 URL 생성 ---
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(API_URL)
                .queryParam("serviceKey", apiKey)
                .queryParam("pageNo", "1")
                .queryParam("numOfRows", "1000")
                .queryParam("dataType", "JSON")
                .queryParam("base_date", baseDate)
                .queryParam("base_time", baseTime)
                .queryParam("nx", SEOSAN_NX)
                .queryParam("ny", SEOSAN_NY);

        // String 대신 URI 객체로 만듭니다.
        URI uri = builder.build(true).toUri();
        // -----------------------------------------------------------

        System.out.println("Request URL: " + uri);

        try {
            // String url 대신 URI 객체를 전달합니다.
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
            // 기상청 API가 오류 메시지를 보냈는지 확인
            if (root.path("response").path("header").has("resultMsg")) {
                String errorMsg = root.path("response").path("header").path("resultMsg").asText();
                System.out.println("기상청 API 오류: " + errorMsg);
            }
            return new WeatherDTO("정보 없음", "정보 없음", "정보 없음", new ArrayList<>());
        }

        String currentTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HH00"));
        String todayDate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        String currentTemp = findCurrentValue(items, todayDate, currentTime, "T1H");
        String currentSkyCode = findCurrentValue(items, todayDate, currentTime, "SKY");
        String currentPtyCode = findCurrentValue(items, todayDate, currentTime, "PTY");

        Map<String, DailyForecast> forecastMap = StreamSupport.stream(items.spliterator(), false)
                .collect(Collectors.groupingBy(
                        item -> item.get("fcstDate").asText(),
                        Collectors.collectingAndThen(Collectors.toList(), list -> {
                            DailyForecast forecast = new DailyForecast();
                            forecast.setDate(list.get(0).get("fcstDate").asText());
                            list.forEach(item -> {
                                switch (item.get("category").asText()) {
                                    case "TMX": forecast.setTempMax(item.get("fcstValue").asText()); break;
                                    case "TMN": forecast.setTempMin(item.get("fcstValue").asText()); break;
                                    case "SKY":
                                        int time = Integer.parseInt(item.get("fcstTime").asText());
                                        if (time >= 600 && time < 1800) {
                                            if (forecast.getSkyAm() == null) forecast.setSkyAm(getSkyStatus(item.get("fcstValue").asText()));
                                        } else {
                                            if (forecast.getSkyPm() == null) forecast.setSkyPm(getSkyStatus(item.get("fcstValue").asText()));
                                        }
                                        break;
                                }
                            });
                            if (forecast.getSkyPm() == null) forecast.setSkyPm(forecast.getSkyAm());
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
