package hackerton.seosancheck.controller.place;

import hackerton.seosancheck.model.place.TouristPlace;
import hackerton.seosancheck.service.place.TouristPlaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/tourist-places")
@RequiredArgsConstructor
public class TouristPlaceController {

    private final TouristPlaceService service;

    @PostMapping("/import")
    public ResponseEntity<String> importExcel(@RequestParam("file") MultipartFile file) {
        service.importExcel(file);
        return ResponseEntity.ok("엑셀 데이터 업로드 성공");
    }

    @GetMapping
    public ResponseEntity<List<TouristPlace>> getAll() {
        return ResponseEntity.ok(service.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<TouristPlace> getById(@PathVariable Long id) {
        TouristPlace place = service.getById(id);
        if (place == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(place);
    }

    @DeleteMapping("/clear")
    public ResponseEntity<String> clearAll() {
        service.clearAll();
        return ResponseEntity.ok("모든 데이터 삭제 완료");
    }
}
