package hackerton.seosancheck.repository;

import hackerton.seosancheck.entity.DailyWeatherCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface DailyWeatherCacheRepository extends JpaRepository<DailyWeatherCache, Long> {

    /**
     * 시작 날짜와 종료 날짜 사이의 모든 날씨 캐시 데이터를 조회합니다.
     * @param startDate 시작 날짜
     * @param endDate 종료 날짜
     * @return 날씨 캐시 리스트
     */
    List<DailyWeatherCache> findByForecastDateBetween(LocalDate startDate, LocalDate endDate);

    /**
     * 💡 오류 해결: 특정 날짜 이전의 모든 캐시 데이터를 삭제하는 메서드를 추가합니다.
     * @param date 기준 날짜
     */
    @Transactional
    void deleteAllByForecastDateBefore(LocalDate date);
}
