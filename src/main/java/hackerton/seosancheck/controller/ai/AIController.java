package hackerton.seosancheck.controller.ai;

import hackerton.seosancheck.model.ai.TravelPlanResponse;
import hackerton.seosancheck.service.ai.impl.AIServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AIController {

    private final AIServiceImpl aiService;

    @GetMapping("/travel-plan")
    public ResponseEntity<TravelPlanResponse> getPlan(
            @RequestParam String area,
            @RequestParam String category,
            @RequestParam(required = false) String text
    ) {
        return ResponseEntity.ok(aiService.generateTravelPlan(area, category, text));
    }

    @GetMapping("/travel-plans") // 복수형
    public ResponseEntity<List<TravelPlanResponse>> getMultiplePlans(
            @RequestParam String area,
            @RequestParam String category,
            @RequestParam(required = false) String text
    ) {
        return ResponseEntity.ok(aiService.generateMultiplePlans(area, category, text));
    }
}
