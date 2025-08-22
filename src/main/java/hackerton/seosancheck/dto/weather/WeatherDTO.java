package hackerton.seosancheck.dto.weather;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class WeatherDTO {
    private String currentTemperature;
    private String currentSky;
    private String precipitation;
    // This list is now initialized, so it will never be null.
    private List<DailyForecast> weeklyForecast = new ArrayList<>();
}
