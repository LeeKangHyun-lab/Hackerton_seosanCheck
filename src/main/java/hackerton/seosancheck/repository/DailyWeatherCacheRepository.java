package hackerton.seosancheck.repository;

import hackerton.seosancheck.entity.DailyWeatherCache;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;

public interface DailyWeatherCacheRepository extends JpaRepository<DailyWeatherCache, LocalDate> {
    List<DailyWeatherCache> findByForecastDateBetween(LocalDate startDate, LocalDate endDate);
}