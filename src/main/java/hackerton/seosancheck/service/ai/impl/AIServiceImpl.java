package hackerton.seosancheck.service.ai.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import hackerton.seosancheck.mapper.place.StoreMapper;
import hackerton.seosancheck.mapper.place.TouristPlaceMapper;
import hackerton.seosancheck.model.ai.TravelConditions;
import hackerton.seosancheck.model.ai.TravelItem;
import hackerton.seosancheck.model.ai.TravelPlanResponse;
import hackerton.seosancheck.model.place.Store;
import hackerton.seosancheck.model.place.TouristPlace;
import hackerton.seosancheck.service.ai.AiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AIServiceImpl implements AiService {

    private final StoreMapper storeMapper;
    private final TouristPlaceMapper touristPlaceMapper;
    private final RestTemplate restTemplate;

    @Value("${openai.api-key}")
    private String apiKey;

    private static final String API_URL = "https://api.openai.com/v1/chat/completions";

    // ====== 조건 추출용 키워드/정규식 ======
    private static final Map<String, String> AREA_MAP = Map.ofEntries(
            Map.entry("바다", "바다"),
            Map.entry("바닷가", "바다"),
            Map.entry("해변", "바다"),
            Map.entry("바다가 보이는", "바다"),
            Map.entry("내륙", "내륙"),
            Map.entry("산", "내륙"),
            Map.entry("계곡", "내륙"),
            Map.entry("숲", "내륙"),
            Map.entry("도시", "내륙")
    );

    private static final Set<String> THEME_KEYWORDS = new HashSet<>(Arrays.asList(
            "감성", "감성적", "감성적인", "힐링", "먹방", "인생샷",
            "역사", "문화", "생태", "자연", "체험", "가벼운 당일치기",
            "친구", "가족", "지역화폐", "로맨틱", "포토스팟"
    ));

    private static final List<String> COMPANION_KEYWORDS = List.of(
            "엄마", "아빠", "부모님", "형", "누나", "언니", "오빠", "동생",
            "가족", "친구", "연인", "커플", "아이", "혼자", "솔로"
    );

    private static final Pattern DURATION_PATTERN = Pattern.compile("(\\d+)박\\s*(\\d+)일|당일치기|당일|하루|주말|1박2일|2박3일|3박4일");

    // ====== 자연어 조건 추출 ======
    public TravelConditions extractConditions(String sentence) {
        if (sentence == null) sentence = "";
        String normalized = sentence.replaceAll("\\s+", " ").trim();

        String companion = COMPANION_KEYWORDS.stream()
                .filter(normalized::contains)
                .findFirst().orElse(null);

        String duration = null;
        Matcher m = DURATION_PATTERN.matcher(normalized.replaceAll("\\s+", ""));
        if (m.find()) {
            duration = m.group();
            if ("당일".equals(duration)) duration = "당일치기";
            if (duration.matches("\\d+박\\d+일")) {
                duration = duration.replace("박", "박 ").replace("일", "일");
            }
        }

        String area = null;
        for (Map.Entry<String, String> e : AREA_MAP.entrySet()) {
            if (normalized.contains(e.getKey())) {
                area = e.getValue();
                break;
            }
        }

        String theme = THEME_KEYWORDS.stream()
                .filter(normalized::contains)
                .findFirst().orElse(null);
        if (theme != null && theme.startsWith("감성")) theme = "감성적인";

        if (duration == null) duration = "당일치기";

        return TravelConditions.builder()
                .companion(companion)
                .theme(theme)
                .duration(duration)
                .area(area)
                .build();
    }

    @Override
    public List<TravelPlanResponse> generateMultiplePlans(String area, String category, String text) {
        // 1) 관광지 5개 추출 (랜덤 페이지)
        List<TouristPlace> places = new ArrayList<>();
        try {
            int total = touristPlaceMapper.countByAreaCategory(area, category);
            int limit = 15;
            int offset = 0;
            if (total > limit) {
                int maxOffset = Math.max(0, total - limit);
                offset = ThreadLocalRandom.current().nextInt(maxOffset + 1);
            }
            places = touristPlaceMapper.findPageByAreaAndCategory(area, category, limit, offset);
        } catch (Exception e) {
            log.error("Failed to fetch places by area/category. area={}, category={}", area, category, e);
            places = List.of();
        }

        // 2) 근처 가게 20개(없으면 최근 20개)
        List<Store> stores;
        if (!places.isEmpty()) {
            double minLat = places.stream().mapToDouble(TouristPlace::getLatitude).min().orElse(0);
            double maxLat = places.stream().mapToDouble(TouristPlace::getLatitude).max().orElse(0);
            double minLon = places.stream().mapToDouble(TouristPlace::getLongitude).min().orElse(0);
            double maxLon = places.stream().mapToDouble(TouristPlace::getLongitude).max().orElse(0);
            double pad = 0.02;
            List<Store> inBox = storeMapper.findStoresInBox(minLat - pad, maxLat + pad, minLon - pad, maxLon + pad, 20);
            stores = (inBox != null && !inBox.isEmpty()) ? inBox : storeMapper.findRecent(15);
        } else {
            stores = storeMapper.findRecent(15);
        }

        // 3) 프롬프트 구성: 한 번에 3개의 코스 요청
        StringBuilder prompt = new StringBuilder();
        prompt.append("서산에서 ").append(area).append(" 지역의 '").append(category)
                .append("' 카테고리에 맞는 코스를 3개 만들어줘.\n")
                .append("각 코스는 '관광지-가게-관광지-관광지-가게' 순서로 총 5개 장소를 포함해야 해.\n")
                .append("3개의 코스는 서로 다른 장소로 구성해. 어떤 코스에도 같은 관광지나 가게가 중복되면 안 돼.\n")
                .append("아래 목록에 있는 이름만 사용해. 목록에 없는 이름은 절대 쓰지 마.\n")
                .append("동선은 서로 가까운 곳 위주로 구성해줘.\n");

        if (text != null && !text.isBlank()) {
            prompt.append("사용자 요청 조건: ").append(text).append("\n")
                    .append("설명은 감성적이면서 따뜻하게 작성.\n");
        }

        prompt.append("\n[관광지]\n");
        for (TouristPlace p : places) {
            prompt.append("- ").append(p.getName())
                    .append(" (").append(p.getCategory()).append(", ").append(p.getArea()).append(")\n");
        }

        prompt.append("\n[가게]\n");
        for (Store s : stores) {
            String tag = (s.getTag() != null && !s.getTag().isBlank()) ? s.getTag() : "일반";
            prompt.append("- ").append(s.getName()).append(" (").append(tag).append(")\n");
        }

        prompt.append("\n반드시 다음의 JSON 형식으로만 응답해:\n")
                .append("{\n")
                .append("  \"plans\": [\n")
                .append("    {\n")
                .append("      \"summary\": \"16자 내 감성 한 문장\",\n")
                .append("      \"course\": [\n")
                .append("        {\"order\": 1, \"type\": \"관광지\", \"name\": \"<관광지명>\", \"description\": \"자세한 설명\"},\n")
                .append("        {\"order\": 2, \"type\": \"가게\", \"name\": \"<가게명>\", \"description\": \"자세한 설명\"},\n")
                .append("        {\"order\": 3, \"type\": \"관광지\", \"name\": \"<관광지명>\", \"description\": \"자세한 설명\"},\n")
                .append("        {\"order\": 4, \"type\": \"관광지\", \"name\": \"<관광지명>\", \"description\": \"자세한 설명\"},\n")
                .append("        {\"order\": 5, \"type\": \"가게\", \"name\": \"<가게명>\", \"description\": \"자세한 설명\"}\n")
                .append("      ]\n")
                .append("    },\n")
                .append("    { ... 또 하나 },\n")
                .append("    { ... 또 하나 }\n")
                .append("  ]\n")
                .append("}\n")
                .append("중요: 오직 위 JSON만. 텍스트를 덧붙이지 마.");

        // 4) OpenAI 한 번만 호출
        List<TravelPlanResponse> results = new ArrayList<>();
        try {
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

            ResponseEntity<String> response = restTemplate.exchange(API_URL, HttpMethod.POST, entity, String.class);

            String aiText = "";
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                ObjectMapper mapper = new ObjectMapper();
                Map<String, Object> root = mapper.readValue(response.getBody(), Map.class);
                List<Map<String, Object>> choices = (List<Map<String, Object>>) root.get("choices");
                if (choices != null && !choices.isEmpty()) {
                    Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                    aiText = (String) message.get("content");
                }
            }

            if (aiText != null && !aiText.isBlank()) {
                int start = aiText.indexOf("{");
                int end = aiText.lastIndexOf("}");
                if (start >= 0 && end > start) {
                    String jsonPart = aiText.substring(start, end + 1);
                    ObjectMapper mapper = new ObjectMapper();
                    Map<String, Object> parsed = mapper.readValue(jsonPart, Map.class);

                    List<Map<String, Object>> plans = (List<Map<String, Object>>) parsed.get("plans");

                    if (plans != null) {
                        Set<String> usedNames = new HashSet<>();

                        for (Map<String, Object> plan : plans) {
                            String summary = (String) plan.getOrDefault("summary", "");
                            List<Map<String, Object>> aiCourse = (List<Map<String, Object>>) plan.get("course");

                            // 1) 이미 사용된 이름 제거
                            List<Map<String, Object>> filteredCourse = aiCourse.stream()
                                    .filter(item -> {
                                        String name = (String) item.get("name");
                                        if (usedNames.contains(name)) {
                                            return false;
                                        }
                                        usedNames.add(name);
                                        return true;
                                    })
                                    .collect(Collectors.toList());

                            // 2) 빠진 자리 채우기 (랜덤)
                            while (filteredCourse.size() < aiCourse.size()) {
                                boolean needTourist = filteredCourse.size() % 2 == 0; // 예: order 1=관광지, 2=가게, 번갈아
                                if (needTourist) {
                                    TouristPlace extra = places.stream()
                                            .filter(p -> !usedNames.contains(p.getName()))
                                            .findAny()
                                            .orElse(null);
                                    if (extra != null) {
                                        Map<String, Object> newItem = Map.of(
                                                "order", filteredCourse.size() + 1,
                                                "type", "관광지",
                                                "name", extra.getName(),
                                                "description", "추가된 관광지입니다."
                                        );
                                        usedNames.add(extra.getName());
                                        filteredCourse.add(newItem);
                                    } else break;
                                } else {
                                    Store extra = stores.stream()
                                            .filter(s -> !usedNames.contains(s.getName()))
                                            .findAny()
                                            .orElse(null);
                                    if (extra != null) {
                                        Map<String, Object> newItem = Map.of(
                                                "order", filteredCourse.size() + 1,
                                                "type", "가게",
                                                "name", extra.getName(),
                                                "description", "추가된 가게입니다."
                                        );
                                        usedNames.add(extra.getName());
                                        filteredCourse.add(newItem);
                                    } else break;
                                }
                            }

                            // 3) DB 매칭 후 TravelItem 변환
                            List<TravelItem> courseItems = mapToCourseItems(filteredCourse, places, stores);
                            results.add(new TravelPlanResponse(summary, courseItems));
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("OpenAI 다중 플랜 호출/파싱 오류", e);
            // 실패 시 빈 리스트 반환 (컨트롤러에서 처리)
        }

        return results;
    }

    /**
     * AI가 준 course JSON을 실제 DB 엔티티에 매칭해 TravelItem 리스트로 변환
     * 목록에 없는 이름은 스킵하여 안전하게 처리
     */
    private List<TravelItem> mapToCourseItems(List<Map<String, Object>> aiCourse,
                                              List<TouristPlace> places,
                                              List<Store> stores) {
        if (aiCourse == null) return List.of();

        return aiCourse.stream().map(item -> {
            try {
                int order = (Integer) item.get("order");
                String type = (String) item.get("type");
                String name = (String) item.get("name");
                String description = (String) item.get("description");

                if ("관광지".equals(type)) {
                    TouristPlace match = places.stream()
                            .filter(p -> p.getName().equals(name))
                            .findFirst().orElse(null);
                    if (match != null) {
                        return new TravelItem(order, match.getId(), type, name, description,
                                match.getAddress(), match.getLatitude(), match.getLongitude(),
                                match.getImageUrl(), null);
                    }
                } else if ("가게".equals(type)) {
                    Store match = stores.stream()
                            .filter(s -> s.getName().equals(name))
                            .findFirst().orElse(null);
                    if (match != null) {
                        return new TravelItem(order, match.getId(), type, name, description,
                                match.getAddress(), match.getLatitude(), match.getLongitude(),
                                null, match.getTag());
                    }
                }
            } catch (Exception ignore) {
                // 개별 아이템 실패는 전체를 막지 않음
            }
            return null;
        }).filter(Objects::nonNull).collect(Collectors.toList());
    }
}
