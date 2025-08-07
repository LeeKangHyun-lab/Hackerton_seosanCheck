package hackerton.seosancheck.model.place;

import lombok.Data;

@Data
public class TouristPlace {
    private Long id;
    private String name;          // 관광명소명
    private String address;       // 주소
    private Double latitude;      // 위도
    private Double longitude;     // 경도
    private String description;   // 해설
    private String referenceDate; // 데이터 기준일자
    private String area;          // 지역
    private String category;      // 관심사
    private String imageUrl;      // 이미지 URL
}
