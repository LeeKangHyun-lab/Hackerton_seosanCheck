package hackerton.seosancheck.controller.ai;

import hackerton.seosancheck.service.ai.impl.AIServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AIController {

    private final AIServiceImpl aiService;

    @GetMapping("/travel-plan")
    public String getTravelPlan() {
        return aiService.generateTravelPlan(); // 파라미터 없음
    }
}
