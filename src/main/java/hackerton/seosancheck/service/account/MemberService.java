package hackerton.seosancheck.service.account;

import hackerton.seosancheck.dto.account.request.MemberJoinRequest;
import hackerton.seosancheck.dto.account.request.MemberLoginRequest;
import hackerton.seosancheck.model.account.Member;

public interface MemberService {

    Member join(MemberJoinRequest request);

    Member login(MemberLoginRequest request);

    void logout(int memberId);

    void saveRefreshToken(int memberId, String token, long expiry);

    void softDeleteAccount(int memberId);

}
