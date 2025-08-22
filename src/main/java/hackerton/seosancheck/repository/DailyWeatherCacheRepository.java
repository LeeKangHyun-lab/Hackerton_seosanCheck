package hackerton.seosancheck.repository;

import hackerton.seosancheck.entity.DailyWeatherCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface DailyWeatherCacheRepository extends JpaRepository<DailyWeatherCache, LocalDate> {

    // 시작 날짜와 종료 날짜 사이의 모든 캐시 데이터를 조회
    List<DailyWeatherCache> findByForecastDateBetween(LocalDate startDate, LocalDate endDate);

    // 특정 날짜 이전의 모든 캐시 데이터를 삭제
    void deleteAllByForecastDateBefore(LocalDate date);
}
