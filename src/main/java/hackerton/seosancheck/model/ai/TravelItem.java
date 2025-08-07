package hackerton.seosancheck.model.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TravelItem {
    private int order;
    private String type; // 관광지 or 가게
    private String name;
    private String description; // AI가 생성한 간단 설명
    private String address;
    private double latitude;
    private double longitude;
    private String imageUrl; // 관광지만 있을 수 있음
}
