package hackerton.seosancheck.dto.account.request;


import lombok.Data;

@Data
public class MemberLoginRequest {
    private String userId;
    private String userPw;
}
