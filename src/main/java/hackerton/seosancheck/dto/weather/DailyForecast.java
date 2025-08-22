package hackerton.seosancheck.dto.weather;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DailyForecast {
    private String date;
    private String tempMax;
    private String tempMin;
    private String skyAm;
    private String skyPm;
}
