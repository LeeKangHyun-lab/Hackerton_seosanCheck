package hackerton.seosancheck.service.place.impl;

import hackerton.seosancheck.mapper.place.TouristPlaceMapper;
import hackerton.seosancheck.model.place.TouristPlace;
import hackerton.seosancheck.service.place.TouristPlaceService;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TouristPlaceServiceImpl implements TouristPlaceService {

    private final TouristPlaceMapper mapper;

    @Override
    public void importExcel(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(inputStream)) {

            mapper.deleteAll();

            Sheet sheet = workbook.getSheetAt(0);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                TouristPlace place = new TouristPlace();
                place.setName(getString(row.getCell(0))); // 관광명소명
                place.setAddress(getString(row.getCell(1))); // 주소

                String gps = getString(row.getCell(2)); // 위치(GPS)
                if (gps.contains(",")) {
                    String[] parts = gps.split(",");
                    place.setLatitude(Double.parseDouble(parts[0].trim()));
                    place.setLongitude(Double.parseDouble(parts[1].trim()));
                }

                place.setDescription(getString(row.getCell(3)));    // 해설
                place.setReferenceDate(getString(row.getCell(4)));  // 데이터 기준일자
                place.setArea(getString(row.getCell(5)));           // 지역
                place.setCategory(getString(row.getCell(6)));       // 관심사
                place.setImageUrl(getString(row.getCell(7)));       // 이미지 URL
                place.setFinalType(getString(row.getCell(8)));      // 최종 분류

                mapper.insert(place);
            }
        } catch (Exception e) {
            throw new RuntimeException("엑셀 업로드 실패: " + e.getMessage());
        }
    }

    @Override
    public List<TouristPlace> getAll() {
        return mapper.selectAll();
    }

    @Override
    public TouristPlace getById(Long id) {
        return mapper.selectById(id);
    }

    @Override
    public void clearAll() {
        mapper.deleteAll();
    }

    private String getString(Cell cell) {
        return cell == null ? "" : cell.toString().trim();
    }
}
