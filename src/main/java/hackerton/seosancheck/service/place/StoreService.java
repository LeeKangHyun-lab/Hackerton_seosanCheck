package hackerton.seosancheck.service.place;

import hackerton.seosancheck.model.place.Store;
import org.apache.poi.ss.usermodel.Cell;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface StoreService {
    void importExcel(MultipartFile file);

    List<Store> getAllStores();

//    Store getStoreById(Long id);

}
