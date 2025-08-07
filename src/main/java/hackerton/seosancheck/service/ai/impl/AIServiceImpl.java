package hackerton.seosancheck.service.ai;

import hackerton.seosancheck.mapper.place.StoreMapper;
import hackerton.seosancheck.mapper.place.TouristPlaceMapper;
import hackerton.seosancheck.model.place.Store;
import hackerton.seosancheck.model.place.TouristPlace;
import hackerton.seosancheck.model.ai.ChatRequest;
import hackerton.seosancheck.model.ai.ChatResponse;
import hackerton.seosancheck.model.ai.Message;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AIService {

    private final StoreMapper storeMapper;
    private final TouristPlaceMapper touristPlaceMapper;

    @Value("${openai.api-key}")
    private String apiKey;

    private static final String API_URL = "https://api.openai.com/v1/chat/completions";

    public String generateTravelPlan(String area) {
        // 1. DB 데이터 가져오기
        List<TouristPlace> places = touristPlaceMapper.findTouristPlacesByArea(area);
        List<Store> stores = storeMapper.findStoresByLocation(area);

        // 2. 프롬프트 작성
        StringBuilder prompt = new StringBuilder();
        prompt.append("사용자는 ").append(area).append(" 지역에서 1박 2일 여행 코스를 원합니다.\n");
        prompt.append("아래 관광지와 식당을 참고하여 JSON 형식으로 추천해 주세요.\n\n");
        prompt.append("관광지 목록:\n");
        for (TouristPlace p : places) {
            prompt.append("- ").append(p.getName()).append(" (").append(p.getCategory()).append(")\n");
        }
        prompt.append("\n식당 목록:\n");
        for (Store s : stores) {
            prompt.append("- ").append(s.getName()).append(" (").append(s.getType()).append(")\n");
        }
        prompt.append("\n응답 형식:\n");
        prompt.append("{ \"day1\": [\"관광지1\", \"식당1\"], \"day2\": [\"관광지2\", \"식당2\"] }");

        // 3. OpenAI API 호출
        RestTemplate restTemplate = new RestTemplate();

        ChatRequest chatRequest = new ChatRequest("gpt-4o-mini", List.of(
                new Message("system", "You are a helpful travel planner."),
                new Message("user", prompt.toString())
        ));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        HttpEntity<ChatRequest> entity = new HttpEntity<>(chatRequest, headers);

        ResponseEntity<ChatResponse> response =
                restTemplate.exchange(API_URL, HttpMethod.POST, entity, ChatResponse.class);

        return response.getBody().getChoices().get(0).getMessage().getContent();
    }
}