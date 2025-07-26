package hackerton.seosancheck.entity.account;

import lombok.Data;

@Data
public class RefreshToken {
    private int id;
    private int memberId;
    private String token;
    private long expiryDate; // 밀리초 단위
}