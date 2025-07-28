package hackerton.seosancheck.dto.account.request;

import lombok.Data;

@Data
public class MemberJoinRequest {
    private String userId;
    private String userPw;
    private String nickname;
}