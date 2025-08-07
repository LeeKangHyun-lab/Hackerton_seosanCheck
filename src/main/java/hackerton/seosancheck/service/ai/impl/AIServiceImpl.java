package hackerton.seosancheck.service.ai.impl;

import hackerton.seosancheck.mapper.place.StoreMapper;
import hackerton.seosancheck.mapper.place.TouristPlaceMapper;
import hackerton.seosancheck.model.place.Store;
import hackerton.seosancheck.model.place.TouristPlace;
import hackerton.seosancheck.service.ai.AiService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AIServiceImpl implements AiService {

    private final StoreMapper storeMapper;
    private final TouristPlaceMapper touristPlaceMapper;
    private final RestTemplate restTemplate;

    @Value("${openai.api-key}")
    private String apiKey;

    private static final String API_URL = "https://api.openai.com/v1/chat/completions";

    public String generateTravelPlan() {
        // 1. 데이터 샘플링 (랜덤 5개씩만 사용)
        List<TouristPlace> places = touristPlaceMapper.findRandom(5);  // 이 메서드는 직접 구현 필요
        List<Store> stores = storeMapper.findRandom(5);                // 이 메서드도 구현 필요

        // 2. 간결한 프롬프트 생성
        StringBuilder prompt = new StringBuilder();
        prompt.append("서산에서 당일치기 여행 코스를 추천해줘.\n");
        prompt.append("관광지와 가게는 다음과 같아:\n\n");

        prompt.append("[관광지]\n");
        for (TouristPlace place : places) {
            prompt.append("- ").append(place.getName())
                    .append(" (").append(place.getCategory()).append(", ").append(place.getArea()).append(")\n");
        }

        prompt.append("\n[가게]\n");
        for (Store store : stores) {
            prompt.append("- ").append(store.getName())
                    .append(" (").append(store.getType()).append(", ").append(store.getLocation()).append(")\n");
        }

        prompt.append("\n위 장소 중 3개를 관광지, 가게, 관광지 순서로 추천해줘.");

        // 3. OpenAI API 요청
        Map<String, Object> requestBody = Map.of(
                "model", "gpt-4o-mini",
                "messages", List.of(
                        Map.of("role", "system", "content", "당신은 여행 코디네이터입니다."),
                        Map.of("role", "user", "content", prompt.toString())
                )
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(API_URL, entity, Map.class);

        Map<String, Object> responseBody = response.getBody();
        if (responseBody != null && responseBody.containsKey("choices")) {
            List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
            if (!choices.isEmpty()) {
                Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                return (String) message.get("content");
            }
        }
        return "여행 코스를 생성할 수 없습니다.";
    }
}
