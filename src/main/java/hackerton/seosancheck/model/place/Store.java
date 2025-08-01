package hackerton.seosancheck.entity.place;

import jakarta.persistence.*;
import lombok.Data;

@Data
public class Store {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;          // 가맹점명
    private String baseAddress;   // 기본주소 (도로명)
    private String detailAddress; // 상세주소
    private String location;      // 소재지(법정동)
    private String type;          // 가맹점유형
    private Double mapx;          // 경도
    private Double mapy;          // 위도
}
