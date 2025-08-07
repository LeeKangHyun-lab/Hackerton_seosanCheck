package hackerton.seosancheck.service.ai.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import hackerton.seosancheck.mapper.place.StoreMapper;
import hackerton.seosancheck.mapper.place.TouristPlaceMapper;
import hackerton.seosancheck.model.ai.TravelItem;
import hackerton.seosancheck.model.ai.TravelPlanResponse;
import hackerton.seosancheck.model.place.Store;
import hackerton.seosancheck.model.place.TouristPlace;
import hackerton.seosancheck.service.ai.AiService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AIServiceImpl implements AiService {

    private final StoreMapper storeMapper;
    private final TouristPlaceMapper touristPlaceMapper;
    private final RestTemplate restTemplate;
    private final ResourceLoader resourceLoader;

    @Value("${openai.api-key}")
    private String apiKey;

    private static final String API_URL = "https://api.openai.com/v1/chat/completions";

    private String loadPromptTemplate(String filePath) {
        try {
            Resource resource = resourceLoader.getResource("classpath:" + filePath);
            return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("프롬프트 파일 로딩 실패", e);
        }
    }

    @Override
    public TravelPlanResponse generateTravelPlan(String area, String category, String text) {
        List<TouristPlace> places = touristPlaceMapper.findRandomByAreaAndCategory(area, category, 5);
        List<Store> stores = storeMapper.findRandom(10);

        // 1. 템플릿 불러오기
        String template = loadPromptTemplate("prompt/ai_Prompt2");

        // 2. 데이터 변환
        String placeList = places.stream()
                .map(p -> "- " + p.getName() + " (" + p.getCategory() + ", " + p.getArea() + ")")
                .collect(Collectors.joining("\n"));

        String storeList = stores.stream()
                .map(s -> "- " + s.getName() + " (" + s.getType() + ", " + s.getLocation() + ")")
                .collect(Collectors.joining("\n"));

        String userCondition = "";
        if (text != null && !text.isBlank()) {
            userCondition = "사용자 요청 조건: " + text + "\n" +
                    "사용자 요청 조건이 논리에 맞지 않거나, 음식점과 관련이 없을 경우 다시 입력해주세요 라고 출력해줘.\n" +
                    "가게 추천 시 tag 필드(매운맛, 채식 등) 고려해줘.";
        }

        // 3. 프롬프트 치환
        String finalPrompt = template
                .replace("{area}", area)
                .replace("{category}", category)
                .replace("{placeList}", placeList)
                .replace("{storeList}", storeList)
                .replace("{userCondition}", userCondition);

        // 4. OpenAI 요청
        Map<String, Object> requestBody = Map.of(
                "model", "gpt-4o-mini",
                "messages", List.of(
                        Map.of("role", "system", "content", "당신은 여행 코디네이터입니다."),
                        Map.of("role", "user", "content", finalPrompt)
                )
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(API_URL, entity, Map.class);

        // 5. 응답 파싱
        String aiText = "";
        String summary = "";
        List<TravelItem> course = List.of();

        try {
            if (response.getBody() != null && response.getBody().containsKey("choices")) {
                Map<String, Object> message = (Map<String, Object>) ((List<?>) response.getBody().get("choices")).get(0);
                Map<String, Object> contentMap = (Map<String, Object>) message.get("message");
                aiText = (String) contentMap.get("content");

                int start = aiText.indexOf("{");
                int end = aiText.lastIndexOf("}");
                if (start >= 0 && end > start) {
                    String jsonPart = aiText.substring(start, end + 1);
                    ObjectMapper mapper = new ObjectMapper();
                    Map<String, Object> parsed = mapper.readValue(jsonPart, Map.class);

                    summary = (String) parsed.get("summary");
                    List<Map<String, Object>> aiCourse = (List<Map<String, Object>>) parsed.get("course");

                    course = aiCourse.stream().map(item -> {
                        int order = (Integer) item.get("order");
                        String type = (String) item.get("type");
                        String name = (String) item.get("name");
                        String description = (String) item.get("description");

                        if ("관광지".equals(type)) {
                            TouristPlace match = places.stream()
                                    .filter(p -> p.getName().equals(name)).findFirst().orElse(null);
                            if (match != null) {
                                return new TravelItem(order, type, name, description,
                                        match.getAddress(), match.getLatitude(), match.getLongitude(),
                                         match.getImageUrl());
                            }
                        } else if ("가게".equals(type)) {
                            Store match = stores.stream()
                                    .filter(s -> s.getName().equals(name)).findFirst().orElse(null);
                            if (match != null) {
                                return new TravelItem(order, type, name, description,
                                        match.getAddress(), match.getLatitude(), match.getLongitude(),
                                        match.getTag());
                            }
                        }
                        return null;
                    }).filter(item -> item != null).collect(Collectors.toList());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            summary = "AI 응답 파싱 중 오류 발생";
            course = List.of();
        }

        return new TravelPlanResponse(summary, course);
    }

    @Override
    public List<TravelPlanResponse> generateMultiplePlans(String area, String category, String text) {
        List<TravelPlanResponse> plans = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            plans.add(generateTravelPlan(area, category, text));
        }
        return plans;
    }
}
