package hackerton.seosancheck.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.*; // lombok 전체 import

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter // 💡 DB 값을 업데이트하기 위해 이 어노테이션을 추가해주세요!
@Builder
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
