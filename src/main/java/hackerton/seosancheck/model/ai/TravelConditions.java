package hackerton.seosancheck.model.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class TravelConditions {
    private String companion; // 예: 엄마, 친구, 가족, 연인, 아이, 형, 누나, 동생
    private String theme;     // 예: 감성적인, 힐링, 먹방 등
    private String duration;  // 예: 당일치기, 1박 2일, 2박 3일
    private String area;      // 예: 바다, 내륙
}
