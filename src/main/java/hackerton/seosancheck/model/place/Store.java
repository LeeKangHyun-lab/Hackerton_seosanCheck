package hackerton.seosancheck.model.place;


import lombok.Data;

@Data
public class Store {

    private Long id;
    private String name;          // 가맹점명
    private String address;   // 기본주소 (도로명)
    private String detailAddress; // 상세주소
    private String location;      // 소재지(법정동)
    private String type;          // 가맹점유형
    private Double longitude;          // 경도
    private Double latitude;          // 위도
}
