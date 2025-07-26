package hackerton.seosancheck.service.account;

import hackerton.seosancheck.dto.account.request.MemberJoinRequest;
import hackerton.seosancheck.dto.account.request.MemberLoginRequest;
import hackerton.seosancheck.entity.account.Member;

public interface MemberService {
    Member join(MemberJoinRequest request) throws Exception;
    Member login(MemberLoginRequest request) throws Exception;
    void logout(int memberId);
    void saveRefreshToken(int memberId, String token, long expiry);
}
