package hackerton.seosancheck.model.weather;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class WeatherDTO {
    // --- 실시간 정보 ---
    private String currentTemperature; // 현재 기온
    private String currentSky;         // 현재 하늘 상태
    private String precipitation;      // 현재 강수 형태

    // --- 오늘의 예보 정보 ---
    private String todayTempMax;       // 오늘 예보 최고 기온
    private String todayTempMin;       // 오늘 예보 최저 기온

    // --- 주간 예보 (내일부터) ---
    private List<DailyForecast> weeklyForecast;
}
