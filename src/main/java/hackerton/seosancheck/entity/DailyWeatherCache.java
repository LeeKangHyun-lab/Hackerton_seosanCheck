package hackerton.seosancheck.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DailyWeatherCache {
    @Id
    private LocalDate forecastDate;
    private LocalDateTime fetchedAt;
    private String tempMax;
    private String tempMin;
    private String skyAm;
    private String skyPm;
}