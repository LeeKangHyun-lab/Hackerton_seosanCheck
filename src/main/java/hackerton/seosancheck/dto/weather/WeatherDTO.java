package hackerton.seosancheck.dto.weather;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class WeatherDTO {
    private String currentTemperature; // 현재 기온
    private String currentSky;         // 현재 하늘 상태 (예: 맑음, 구름많음)
    private String precipitation;      // 현재 강수 형태 (예: 비, 눈)
    private List<DailyForecast> weeklyForecast; // 주간 예보
}
