package hackerton.seosancheck.controller.ai;

import hackerton.seosancheck.model.ai.TravelConditions;
import hackerton.seosancheck.model.ai.TravelPlanResponse;
import hackerton.seosancheck.service.ai.impl.AIServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Slf4j // 이거 추가
public class AIController {

    private final AIServiceImpl aiService;

//    @GetMapping("/travel-plan")
//    public ResponseEntity<TravelPlanResponse> getPlan(
//            @RequestParam String area,
//            @RequestParam String category,
//            @RequestParam(required = false) String text
//    ) {
//        log.info("[travel-plan] area: {}, category: {}, text: {}", area, category, text);
//        return ResponseEntity.ok(aiService.generateTravelPlan(area, category, text));
//    }

    @GetMapping("/travel-plans")
    public ResponseEntity<List<TravelPlanResponse>> getPlans(
            @RequestParam String area,
            @RequestParam String category,
            @RequestParam(required = false) String text
    ) {
        log.info("[travel-plans] area: {}, category: {}, text: {}", area, category, text);
        return ResponseEntity.ok(aiService.generateMultiplePlans(text, area));
    }

    // 디버그용
    @GetMapping("/extract-conditions")
    public ResponseEntity<TravelConditions> extractConditions(@RequestParam String sentence) {
        log.info("[extract-conditions] sentence: {}", sentence);
        return ResponseEntity.ok(aiService.extractConditions(sentence));
    }
}
