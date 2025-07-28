package hackerton.seosancheck.dto.account.response;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
public class MemberResponse {
    private int id;
    private String userId;
    private String nickname;
    private String accessToken;
    private String refreshToken;
}