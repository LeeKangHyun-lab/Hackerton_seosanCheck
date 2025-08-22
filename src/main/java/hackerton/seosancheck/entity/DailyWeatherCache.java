package hackerton.seosancheck.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder; // 💡 1. Builder 어노테이션 추가
import lombok.Getter;   // 💡 2. @Data 대신 @Getter와 @Setter 사용
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Getter   // @Data는 JPA 엔티티에서 예기치 않은 문제를 일으킬 수 있어 @Getter로 변경
@Setter   // 필요한 경우 @Setter도 추가
@Builder  // ServiceImpl에서 .builder()를 사용하기 위해 필수
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
