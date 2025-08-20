package hackerton.seosancheck.model.weather;

import lombok.Data;

@Data
public class DailyForecast {
    private String date;      // 예보 날짜 (예: 20250820)
    private String tempMax;   // 최고 기온
    private String tempMin;   // 최저 기온
    private String skyAm;     // 오전 하늘 상태
    private String skyPm;     // 오후 하늘 상태
}