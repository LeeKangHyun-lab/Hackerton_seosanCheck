package hackerton.seosancheck.service.account;

import hackerton.seosancheck.common.exception.CustomException;
import hackerton.seosancheck.dto.account.request.MemberJoinRequest;
import hackerton.seosancheck.dto.account.request.MemberLoginRequest;
import hackerton.seosancheck.entity.account.Member;
import hackerton.seosancheck.entity.account.RefreshToken;
import hackerton.seosancheck.mapper.MemberMapper;
import hackerton.seosancheck.mapper.RefreshTokenMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MemberServiceImpl implements MemberService {

    private final MemberMapper memberMapper;
    private final RefreshTokenMapper refreshTokenMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public Member join(MemberJoinRequest request) {
        if (memberMapper.selectCount(request.getUserId()) > 0) {
            throw new CustomException("이미 존재하는 아이디입니다.", HttpStatus.CONFLICT);
        }

        Member member = new Member();
        member.setUserId(request.getUserId());
        member.setNickname(request.getNickname());
        member.setUserPw(passwordEncoder.encode(request.getUserPw()));
        memberMapper.insert(member);
        return member;
    }

    @Override
    public Member login(MemberLoginRequest request) {
        Member member = memberMapper.selectByUserId(request.getUserId());
        if (member == null) {
            throw new CustomException("존재하지 않는 아이디입니다.", HttpStatus.UNAUTHORIZED);
        }

        if (!passwordEncoder.matches(request.getUserPw(), member.getUserPw())) {
            throw new CustomException("비밀번호가 올바르지 않습니다.", HttpStatus.UNAUTHORIZED);
        }

        return member;
    }

    @Override
    public void saveRefreshToken(int memberId, String token, long expiry) {
        refreshTokenMapper.deleteByMemberId(memberId);
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

    @Override
    public void softDeleteAccount(int memberId) {
        refreshTokenMapper.deleteByMemberId(memberId);
        int updatedRows = memberMapper.softDeleteById(memberId);
        if (updatedRows == 0) {
            throw new CustomException("회원 정보를 찾을 수 없습니다.", HttpStatus.NOT_FOUND);
        }
    }
}
