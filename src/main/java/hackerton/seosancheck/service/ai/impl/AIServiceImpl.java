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
            Map.entry("바다", "바다"), Map.entry("바닷가", "바다"), Map.entry("해변", "바다"), Map.entry("바다가 보이는", "바다"),
            Map.entry("내륙", "내륙"), Map.entry("산", "내륙"), Map.entry("계곡", "내륙"), Map.entry("숲", "내륙"), Map.entry("도시", "내륙")
    );

    private static final Set<String> THEME_KEYWORDS = Set.of(
            "감성", "감성적", "감성적인", "힐링", "먹방", "인생샷",
            "역사", "문화", "생태", "자연", "체험", "가벼운 당일치기",
            "친구", "가족", "지역화폐", "로맨틱", "포토스팟", "바쁜", "정신없는"
    );

    private static final List<String> COMPANION_KEYWORDS = List.of(
            "엄마", "아빠", "부모님", "형", "누나", "언니", "오빠", "동생",
            "가족", "친구", "연인", "커플", "아이", "혼자", "솔로", "이모", "고모", "할머니", "할아버지"
    );

    private static final Pattern DURATION_PATTERN =
            Pattern.compile("(\\d+)박\\s*(\\d+)일|당일치기|당일|하루|주말|1박2일|2박3일|3박4일");

    /**
     * 사용자 문장에서 조건 추출 (AI 프롬프트용)
     */
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
    public List<TravelPlanResponse> generateMultiplePlans(String text, String areaParam, String categoryParam) {
        // 1) 사용자 문장에서 조건 추출 (AI 프롬프트용)
        TravelConditions cond = extractConditions(text);
        String areaForAI = cond.getArea() != null ? cond.getArea() : areaParam;
        String themeForAI = cond.getTheme() != null ? cond.getTheme() : "힐링";
        String companionForAI = cond.getCompanion() != null ? cond.getCompanion() : "미정";
        String durationForAI = cond.getDuration() != null ? cond.getDuration() : "당일치기";

        int limit = 15;

        // 2) DB에서 areaParam, categoryParam으로 필터
        List<TouristPlace> places = touristPlaceMapper.findRandomByAreaAndCategory(areaParam, categoryParam, limit);
        List<Store> stores = storeMapper.findRandom(limit);

        if (places.isEmpty() || stores.isEmpty()) {
            log.warn("DB에서 가져온 데이터가 부족합니다. places={}, stores={}", places.size(), stores.size());
        }

        // 3) GPT 프롬프트 구성 (반드시 목록에서만 선택하도록 강제)
        StringBuilder prompt = new StringBuilder();
        prompt.append("당신은 서산 여행 코디네이터입니다.\n")
                .append("다음 조건을 참고하여 서로 다른 3개의 여행 코스를 만들어주세요.\n")
                .append("- 지역: ").append(areaForAI).append("\n")
                .append("- 테마: ").append(themeForAI).append("\n")
                .append("- 동행: ").append(companionForAI).append("\n")
                .append("- 기간: ").append(durationForAI).append("\n")
                .append("\n")
                .append("규칙:\n")
                .append("1. 각 코스는 '관광지-가게-관광지-관광지-가게' 순서의 5개 장소.\n")
                .append("2. 3개의 코스 간에 같은 장소가 중복되면 안 됩니다.\n")
                .append("3. 반드시 아래 목록에서만 선택하세요.\n")
                .append("4 각 코스에는 제목과 감성적인 요약 설명을 붙여주세요.\n")
                .append("5 각 course.item.description에는 반드시 동행(")
                .append(companionForAI)
                .append(")과 테마(")
                .append(themeForAI)
                .append(")의 의도가 드러나야 하며, 30자 이상으로 작성해주세요.")
                .append("6. 'course' 배열의 각 항목은 name, type(관광지/가게), description, order가 있어야 합니다.\n")
                .append("7. JSON만 출력하세요.\n")
                .append("\n[관광지 후보]\n");

        for (TouristPlace p : places) {
            prompt.append("- ").append(p.getName())
                    .append(" (").append(p.getCategory()).append(", ").append(p.getArea()).append(")\n");
        }

        prompt.append("\n[가게 후보]\n");
        for (Store s : stores) {
            String tag = (s.getTag() != null && !s.getTag().isBlank()) ? s.getTag() : "일반";
            prompt.append("- ").append(s.getName()).append(" (").append(tag).append(")\n");
        }

        prompt.append("\n출력 예시:\n")
                .append("{ \"plans\": [\n")
                .append("  {\n")
                .append("    \"summary\": \"코스 설명\",\n")
                .append("    \"course\": [\n")
                .append("      {\"order\": 1, \"type\": \"관광지\", \"name\": \"장소명\", \"description\": \"간단 설명\"}\n")
                .append("    ]\n")
                .append("  }\n")
                .append("] }\n")
                .append("description은 30자 이상으로 자세히 작성해줘, 테마, 동행을 사용해서 해줘, 그리고 문장에 '최지인'이라는 단어를 꼭 넣어줘 예를 들면 '노을 지는 바다를 배경으로 우럭등대를 바라보며, 동생과 함께 궁금한 이야기를 나누고 마음을 열어보세요. 이렇게 해줘")
                .append("summary를 장소들의 특징을 담아 상세히 써주되, 한문장으로 표현해줘. 감성적인 문장으로 넣어주고 16자 내로 해줘, 예를 들어, '시간이 멈춘 골목에서 첫걸을을 떼다' 이런식으로 만들어줘");


        // 4) GPT 호출
        return callOpenAiAndParsePlans(prompt.toString(), places, stores);
    }

    private List<TravelPlanResponse> callOpenAiAndParsePlans(String prompt,
                                                             List<TouristPlace> places,
                                                             List<Store> stores) {
        List<TravelPlanResponse> results = new ArrayList<>();

        try {
            Map<String, Object> requestBody = Map.of(
                    "model", "gpt-4o-mini",
                    "messages", List.of(
                            Map.of("role", "system", "content", "당신은 여행 코디네이터입니다."),
                            Map.of("role", "user", "content", prompt)
                    )
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response =
                    restTemplate.exchange(API_URL, HttpMethod.POST, entity, String.class);

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

                    List<?> rawPlans = (List<?>) parsed.get("plans");
                    if (rawPlans != null) {
                        Set<String> usedNames = new HashSet<>();

                        for (Object obj : rawPlans) {
                            Map<String, Object> plan;

                            // 안전하게 변환 (Map 또는 String 모두 처리)
                            if (obj instanceof Map) {
                                plan = (Map<String, Object>) obj;
                            } else if (obj instanceof String) {
                                try {
                                    plan = mapper.readValue((String) obj, Map.class);
                                } catch (Exception e) {
                                    log.warn("plan 문자열 파싱 실패: {}", obj);
                                    continue;
                                }
                            } else {
                                log.warn("알 수 없는 plan 형식: {}", obj);
                                continue;
                            }

                            String summary = (String) plan.getOrDefault("summary", "");
                            List<?> aiCourseRaw = (List<?>) plan.get("course");

                            List<Map<String, Object>> aiCourse = new ArrayList<>();
                            if (aiCourseRaw != null) {
                                for (Object c : aiCourseRaw) {
                                    if (c instanceof Map) {
                                        aiCourse.add((Map<String, Object>) c);
                                    } else if (c instanceof String) {
                                        try {
                                            aiCourse.add(mapper.readValue((String) c, Map.class));
                                        } catch (Exception e) {
                                            log.warn("course 문자열 파싱 실패: {}", c);
                                        }
                                    }
                                }
                            }

                            // 중복 제거
                            List<Map<String, Object>> filteredCourse = aiCourse.stream()
                                    .filter(item -> {
                                        String name = (String) item.get("name");
                                        if (usedNames.contains(name)) return false;
                                        usedNames.add(name);
                                        return true;
                                    }).collect(Collectors.toList());

                            // 매칭
                            List<TravelItem> courseItems = mapToCourseItems(filteredCourse, places, stores);
                            results.add(new TravelPlanResponse(summary, courseItems));
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("OpenAI 호출/파싱 오류", e);
        }

        return results;
    }

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
            } catch (Exception ignore) {}
            return null;
        }).filter(Objects::nonNull).collect(Collectors.toList());
    }
}
