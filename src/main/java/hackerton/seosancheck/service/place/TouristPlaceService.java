package hackerton.seosancheck.service.place;

import hackerton.seosancheck.model.place.TouristPlace;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface TouristPlaceService {
    void importExcel(MultipartFile file);
    List<TouristPlace> getAll();
    TouristPlace getById(Long id);
    void clearAll();
}
