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

    // 정규식 패턴 매칭 (변형 단어 대응)
    private static final Map<String, Pattern> THEME_PATTERNS = Map.ofEntries(
            Map.entry("감성적인", Pattern.compile("감성.*")),
            Map.entry("힐링", Pattern.compile("힐링.*")),
            Map.entry("먹방", Pattern.compile("먹방.*")),
            Map.entry("인생샷", Pattern.compile("인생샷.*")),
            Map.entry("역사", Pattern.compile("역사.*")),
            Map.entry("문화", Pattern.compile("문화.*")),
            Map.entry("생태", Pattern.compile("생태.*")),
            Map.entry("자연", Pattern.compile("자연.*")),
            Map.entry("체험", Pattern.compile("체험.*")),
            Map.entry("로맨틱", Pattern.compile("로맨틱.*")),
            Map.entry("포토스팟", Pattern.compile("포토스팟.*")),
            Map.entry("바쁜", Pattern.compile("바쁜.*")),
            Map.entry("정신없는", Pattern.compile("정신없는.*"))
    );

    private static final List<String> COMPANION_KEYWORDS = List.of(
            "엄마", "아빠", "부모님", "형", "누나", "언니", "오빠", "동생",
            "가족", "친구", "연인", "커플", "아이", "혼자", "솔로", "이모", "고모", "할머니", "할아버지"
    );

    // 동행자 패턴 (자유 입력)
    private static final Pattern COMPANION_PATTERN =
            Pattern.compile("([가-힣]+)(랑|과|와|하고)");

    private static final Pattern DURATION_PATTERN =
            Pattern.compile("(\\d+)박\\s*(\\d+)일|당일치기|당일|하루|주말|1박2일|2박3일|3박4일");

    /**
     * 테마 추출 (빠른 매칭 → 정규식 → AI 유사도)
     */
    private String extractTheme(String normalized, String originalSentence) {
        // 1) 빠른 매칭
        for (String keyword : THEME_KEYWORDS) {
            if (normalized.contains(keyword)) {
                if (keyword.startsWith("감성")) return "감성적인";
                return keyword;
            }
        }

        // 2) 정규식 매칭
        for (Map.Entry<String, Pattern> entry : THEME_PATTERNS.entrySet()) {
            if (entry.getValue().matcher(normalized).find()) {
                return entry.getKey();
            }
        }

        // 3) AI Fallback
        try {
            String prompt = "다음 문장에서 여행 테마를 추출하세요. " +
                    "아래 리스트 중 가장 가까운 하나만 골라서 정확히 출력하세요. " +
                    "문장에 같은 의미의 변형(예: '정신없이' → '정신없는', '바쁘게' → '바쁜')이 있으면 대응되는 대표 키워드로 통일하세요.\n" +
                    "[감성적인, 힐링, 먹방, 인생샷, 역사, 문화, 생태, 자연, 체험, 로맨틱, 포토스팟, 바쁜, 정신없는, 무서운, 떨리는]\n" +
                    "문장: " + originalSentence;

            Map<String, Object> requestBody = Map.of(
                    "model", "gpt-4o-mini",
                    "messages", List.of(
                            Map.of("role", "system", "content", "당신은 분류기입니다."),
                            Map.of("role", "user", "content", prompt)
                    )
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response =
                    restTemplate.exchange(API_URL, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                ObjectMapper mapper = new ObjectMapper();
                Map<String, Object> root = mapper.readValue(response.getBody(), Map.class);
                List<Map<String, Object>> choices = (List<Map<String, Object>>) root.get("choices");
                if (choices != null && !choices.isEmpty()) {
                    Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                    String aiTheme = (String) message.get("content");
                    return aiTheme.trim();
                }
            }
        } catch (Exception e) {
            log.warn("테마 추론 실패, fallback 사용", e);
        }

        return null; // 최종 fallback
    }

    /**
     * 사용자 문장에서 조건 추출
     */
    public TravelConditions extractConditions(String sentence) {
        if (sentence == null) sentence = "";
        String normalized = sentence.replaceAll("\\s+", " ").trim();

        // 1) 동행자
        String companion = COMPANION_KEYWORDS.stream()
                .filter(normalized::contains)
                .findFirst()
                .orElse(null);

        if (companion == null) {
            Matcher matcher = COMPANION_PATTERN.matcher(normalized);
            if (matcher.find()) {
                companion = matcher.group(1);
            }
        }

        // 2) 기간
        String duration = null;
        Matcher m = DURATION_PATTERN.matcher(normalized.replaceAll("\\s+", ""));
        if (m.find()) {
            duration = m.group();
            if ("당일".equals(duration)) duration = "당일치기";
            if (duration.matches("\\d+박\\d+일")) {
                duration = duration.replace("박", "박 ").replace("일", "일");
            }
        }
        if (duration == null) duration = "당일치기";

        // 3) 지역
        String area = null;
        for (Map.Entry<String, String> e : AREA_MAP.entrySet()) {
            if (normalized.contains(e.getKey())) {
                area = e.getValue();
                break;
            }
        }

        // 4) 테마
        String theme = extractTheme(normalized, sentence);

        return TravelConditions.builder()
                .companion(companion)
                .theme(theme)
                .duration(duration)
                .area(area)
                .build();
    }

    @Override
    public List<TravelPlanResponse> generateMultiplePlans(String text, String areaParam, String categoryParam) {
        // 1) 사용자 문장에서 조건 추출
        TravelConditions cond = extractConditions(text);
        String areaForAI = cond.getArea() != null ? cond.getArea() : areaParam;
        String companionForAI = cond.getCompanion() != null ? cond.getCompanion() : "미정";
        String durationForAI = cond.getDuration() != null ? cond.getDuration() : "당일치기";

        // theme 은 없을 경우 AI fallback 으로 추론
        String themeForAI = cond.getTheme() != null ? cond.getTheme() : extractTheme(text, text);

        int limit = 15;

        // 2) DB 조회
        List<TouristPlace> places = touristPlaceMapper.findRandomByAreaAndCategory(areaParam, categoryParam, limit);
        List<Store> stores = storeMapper.findRandom(limit);

        if (places.isEmpty() || stores.isEmpty()) {
            log.warn("DB에서 가져온 데이터가 부족합니다. places={}, stores={}", places.size(), stores.size());
        }

        // 3) GPT 프롬프트 구성
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
                .append("2. 5개의 코스는 무조건 다 있어야 해요. 장소가 없다면 중복돼도 되니까 무조건 5개의 코스를 다 제공해줘요\n")
                .append("3. 3개의 코스에 가능하면 중복되지 않게 하며, 장소가 부족할 경우에만 중복을 허용합니다.\n")
                .append("4. 반드시 아래 목록에서만 선택하세요.\n")
                .append("5. 각 코스에는 제목과 감성적인 요약 설명을 붙여주세요.\n")
                .append("6. 각 course.item.description에는 반드시 동행(")
                .append(companionForAI)
                .append(")과 테마(")
                .append(themeForAI)
                .append(")의 의도가 반드시 드러나야 하며, 30자 이상으로 작성해주세요.\n")
                .append("7. 'course' 배열의 각 항목은 name, type(관광지/가게), description, order가 있어야 합니다.\n")
                .append("8. JSON만 출력하세요.\n")
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
                .append("description은 30자 이상으로 자세히 작성해줘, 테마, 동행을 사용해서 해줘, 그리고 문장에 '최지인과 함께라면 더 재밌습니다'라는 단어를 꼭 넣어줘.\n")
                .append("summary는 장소들의 특징을 담아 한문장으로 표현해줘. 감성적인 문장으로 16자 내외로 작성해줘.\n");

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

                            List<Map<String, Object>> filteredCourse = aiCourse.stream()
                                    .filter(item -> {
                                        String name = (String) item.get("name");
                                        if (usedNames.contains(name)) return false;
                                        usedNames.add(name);
                                        return true;
                                    }).collect(Collectors.toList());

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
