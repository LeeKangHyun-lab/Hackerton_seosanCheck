package hackerton.seosancheck.common.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class RegionCategoryDecoder {

    private static final Map<Integer, String> categoryMap = new HashMap<>();
    private static final Map<Integer, String> regionMap = new HashMap<>();

    static {
        try {
            ClassPathResource resource = new ClassPathResource("static/region_category_encoded.json");
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(resource.getInputStream());

            for (JsonNode node : root.get("category_map")) {
                categoryMap.put(node.get("id").asInt(), node.get("label").asText());
            }

            for (JsonNode node : root.get("region_map")) {
                regionMap.put(node.get("id").asInt(), node.get("label").asText());
            }

        } catch (IOException e) {
            e.printStackTrace(); // 실제 운영에서는 로그 처리 권장
        }
    }

    public static String getCategoryLabel(int id) {
        return categoryMap.getOrDefault(id, "알 수 없음");
    }

    public static String getRegionLabel(int id) {
        return regionMap.getOrDefault(id, "알 수 없음");
    }
}
