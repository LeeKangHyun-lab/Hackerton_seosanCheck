package hackerton.seosancheck.service.place.impl;

import hackerton.seosancheck.mapper.place.StoreMapper;
import hackerton.seosancheck.model.place.Store;
import hackerton.seosancheck.service.place.StoreService;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StoreServiceImpl implements StoreService {

    private final StoreMapper mapper;

    @Override
    public void importExcel(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(inputStream)) {

            mapper.deleteAll();

            Sheet sheet = workbook.getSheetAt(0);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                org.apache.poi.ss.usermodel.Row row = sheet.getRow(i);
                if (row == null) continue;

                Store store = new Store();
                store.setName(getString(row.getCell(1)));
                store.setAddress(getString(row.getCell(2)));
                store.setDetailAddress(getString(row.getCell(3)));
                store.setLocation(getString(row.getCell(4)));
                store.setType(getString(row.getCell(5)));
                store.setLongitude(getDouble(row.getCell(6)));
                store.setLatitude(getDouble(row.getCell(7)));

                mapper.insert(store);
            }
        } catch (Exception e) {
            throw new RuntimeException("엑셀 업로드 실패: " + e.getMessage());
        }
    }

    @Override
    public List<Store> getAllStores() {
        return mapper.selectAll();
    }

    @Override
    public Store getStoreById(Long id) {
        return mapper.selectById(id);
    }

    private String getString(Cell cell) {
        return cell == null ? "" : cell.toString().trim();
    }

    private Double getDouble(Cell cell) {
        return (cell == null || cell.toString().isEmpty()) ? null : Double.valueOf(cell.toString());
    }

}

