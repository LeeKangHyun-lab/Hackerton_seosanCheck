package hackerton.seosancheck.controller.place;

import hackerton.seosancheck.model.place.Store;
import hackerton.seosancheck.service.place.StoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/store")
@RequiredArgsConstructor
public class StoreController {

    private final StoreService service;

    @PostMapping("/import")
    public ResponseEntity<String> importExcel(@RequestParam("file") MultipartFile file) {
        service.importExcel(file);
        return ResponseEntity.ok("엑셀 데이터 업로드 성공");
    }

    @GetMapping
    public ResponseEntity<List<Store>> getAllStores() {
        return ResponseEntity.ok(service.getAllStores());
    }

    @GetMapping("/{id}")// 단순 id조회라 수정 예정
    public ResponseEntity<Store> getStoreById(@PathVariable Long id) {
        Store store = service.getStoreById(id);
        if (store == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(store);
    }
}
