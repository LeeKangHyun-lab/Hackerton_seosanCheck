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
    import org.springframework.http.*;
    import org.springframework.stereotype.Service;
    import org.springframework.web.client.RestTemplate;

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

        @Value("${openai.api-key}")
        private String apiKey;

        private static final String API_URL = "https://api.openai.com/v1/chat/completions";

        @Override
        public TravelPlanResponse generateTravelPlan(String area, String category, String text) {
            List<TouristPlace> places = touristPlaceMapper.findRandomByAreaAndCategory(area, category, 5);
            List<Store> stores = storeMapper.findRandom(20);

            StringBuilder prompt = new StringBuilder();
            prompt.append("서산에서 ").append(area).append(" 지역의 '").append(category)
                    .append("' 카테고리에 맞는 당일치기 여행 코스를 추천해줘.\n")
                    .append("다음은 관광지와 가게 목록이야:\n\n")
                    .append("[관광지]\n");

            for (TouristPlace place : places) {
                prompt.append("- ").append(place.getName())
                        .append(" (").append(place.getCategory()).append(", ").append(place.getArea()).append(")\n");
            }

            prompt.append("\n[가게]\n");
            for (Store store : stores) {
                prompt.append("- ").append(store.getName())
                        .append(" (").append(store.getType()).append(", ").append(store.getLocation()).append(")\n");
            }

            prompt.append("\n위 장소 중 관광지 3곳과 가게 2곳을 뽑아 관광지-가게-관광지-관광지-가게 순서로 추천해줘. 장소는 앞 장소에서 가까운 곳으로 골라줘.\n");
            if (text != null && !text.isBlank()) {
                prompt.append("사용자 요청 조건: ").append(text).append("\n");
                prompt.append("관광지와 가게 추천 시 tag 필드(아이와 함께 연인과 함께 등) 고려해줘. 그리고 설명을 달아줘\n");
                prompt.append("- 각각의 장소에 대해 아이와 함께하기 좋은 이유를 설명에 꼭 포함해줘.\n");
                prompt.append("- 설명은 감성적으로, 따뜻하게 써줘. 예: '아이의 웃음소리가 퍼지는 모래사장' 같은 느낌으로.\n");

            }

            prompt.append("\n설명 없이 순수 JSON만 응답해줘:\n")
                    .append("{\n")
                    .append("  \"summary\": \"여행 요약\",\n")
                    .append("  \"course\": [\n")
                    .append("    {\"order\": 1, \"type\": \"관광지\", \"name\": \"간월암\", \"description\": \"자세한 설명\"},\n")
                    .append("    {\"order\": 2, \"type\": \"가게\", \"name\": \"양평해장국\", \"description\": \"자세한 설명 \"}\n")
                    .append("  ]\n")
                    .append("}")
                    .append("description은 자세히 작성해줘")
                    .append("summary장소들의 특징을 담아 상세히 써주되, 한문장으로 표현해줘 감성적인 문장으로 넣어주고 16자 내로 해줘, 예를 들어, 시간이 멈춘 골목에서 첫걸을을 뗴다 이런식으로 만들어줘");
    //


            // OpenAI 요청
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
                                    return new TravelItem(order, match.getId(), type, name, description,
                                            match.getAddress(), match.getLatitude(), match.getLongitude(), match.getImageUrl(),null);
                                }
                            } else if ("가게".equals(type)) {
                                Store match = stores.stream()
                                        .filter(s -> s.getName().equals(name)).findFirst().orElse(null);
                                if (match != null) {
                                    return new TravelItem(order, match.getId(), type, name, description,
                                            match.getAddress(), match.getLatitude(), match.getLongitude(), null, match.getTag());
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
                TravelPlanResponse singlePlan = generateTravelPlan(area, category, text);
                plans.add(singlePlan);
            }
            return plans;
        }

    }