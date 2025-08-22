package hackerton.seosancheck.dto.weather;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WeatherDTO {

    // 실시간 정보
    private String currentTemperature;
    private String currentSky;
    private String precipitation;

    // 오늘의 최고/최저 기온
    private String todayTempMax;
    private String todayTempMin;

    // 주간 예보 (오늘 이후 7일)
    private List<DailyForecast> weeklyForecast;

}
