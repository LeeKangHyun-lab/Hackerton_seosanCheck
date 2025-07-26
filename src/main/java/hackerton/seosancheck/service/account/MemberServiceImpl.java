package hackerton.seosancheck.service.account;

import hackerton.seosancheck.dto.account.request.MemberJoinRequest;
import hackerton.seosancheck.dto.account.request.MemberLoginRequest;
import hackerton.seosancheck.entity.account.Member;
import hackerton.seosancheck.entity.account.RefreshToken;
import hackerton.seosancheck.mapper.MemberMapper;
import hackerton.seosancheck.mapper.RefreshTokenMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MemberServiceImpl implements MemberService {

    private final MemberMapper memberMapper;
    private final RefreshTokenMapper refreshTokenMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public Member join(MemberJoinRequest request) throws Exception {
        if (memberMapper.selectCount(request.getUserId()) > 0) {
            throw new RuntimeException("이미 존재하는 아이디입니다.");
        }

        Member member = new Member();
        member.setUserId(request.getUserId());
        member.setUserPw(passwordEncoder.encode(request.getUserPw()));
        memberMapper.insert(member);

        return member;
    }

    @Override
    public Member login(MemberLoginRequest request) throws Exception {
        Member member = memberMapper.selectByUserId(request.getUserId());
        if (member == null || !passwordEncoder.matches(request.getUserPw(), member.getUserPw())) {
            throw new RuntimeException("아이디 또는 비밀번호가 올바르지 않습니다.");
        }
        return member;
    }

    @Override
    public void saveRefreshToken(int memberId, String token, long expiry) {
        refreshTokenMapper.deleteByMemberId(memberId); // 기존 토큰 삭제
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setMemberId(memberId);
        refreshToken.setToken(token);
        refreshToken.setExpiryDate(System.currentTimeMillis() + expiry);
        refreshTokenMapper.insert(refreshToken);
    }

    @Override
    public void logout(int memberId) {
        refreshTokenMapper.deleteByMemberId(memberId);
    }
}
