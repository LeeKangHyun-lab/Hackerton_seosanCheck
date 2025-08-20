package hackerton.seosancheck.controller.weather;

import hackerton.seosancheck.dto.weather.WeatherDTO;
import hackerton.seosancheck.service.weather.WeatherService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/weather")
@RequiredArgsConstructor
public class WeatherController {

    private final WeatherService weatherService;

    // 서산시의 현재 및 주간 날씨 정보를 반환하는 API
    @GetMapping("/seosan")
    public ResponseEntity<WeatherDTO> getSeosanWeather() {
        return ResponseEntity.ok(weatherService.getSeosanWeather());
    }
}