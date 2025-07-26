package hackerton.seosancheck.controller.account;

import hackerton.seosancheck.common.security.JwtTokenProvider;
import hackerton.seosancheck.dto.account.request.MemberJoinRequest;
import hackerton.seosancheck.dto.account.request.MemberLoginRequest;
import hackerton.seosancheck.dto.account.response.MemberResponse;
import hackerton.seosancheck.dto.account.response.TokenResponse;
import hackerton.seosancheck.entity.account.Member;
import hackerton.seosancheck.entity.account.RefreshToken;
import hackerton.seosancheck.mapper.MemberMapper;
import hackerton.seosancheck.mapper.RefreshTokenMapper;
import hackerton.seosancheck.service.account.MemberService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
public class AccountController {

    private final MemberService memberService;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenMapper refreshTokenMapper;
    private final MemberMapper memberMapper;

    @PostMapping("/join")
    public ResponseEntity<MemberResponse> join(@RequestBody MemberJoinRequest request, HttpServletResponse response) throws Exception {
        Member newMember = memberService.join(request);

        String accessToken = jwtTokenProvider.generateAccessToken(newMember.getUserId());
        String refreshToken = jwtTokenProvider.generateRefreshToken(newMember.getUserId());

        memberService.saveRefreshToken(newMember.getId(), refreshToken, jwtTokenProvider.getRefreshTokenExpiry());
        addRefreshTokenCookie(response, refreshToken);

        return ResponseEntity.ok(new MemberResponse(newMember.getId(), newMember.getUserId(), accessToken, refreshToken));
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@RequestBody MemberLoginRequest request, HttpServletResponse response) throws Exception {
        Member loginMember = memberService.login(request);

        String accessToken = jwtTokenProvider.generateAccessToken(loginMember.getUserId());
        String refreshToken = jwtTokenProvider.generateRefreshToken(loginMember.getUserId());

        memberService.saveRefreshToken(loginMember.getId(), refreshToken, jwtTokenProvider.getRefreshTokenExpiry());
        addRefreshTokenCookie(response, refreshToken);

        return ResponseEntity.ok(new TokenResponse(accessToken, refreshToken));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@CookieValue(value = "refresh_token", required = false) String refreshToken) {
        if (refreshToken == null) {
            throw new RuntimeException("Refresh Token이 없습니다.");
        }

        RefreshToken dbToken = refreshTokenMapper.findByToken(refreshToken);
        if (dbToken == null || dbToken.getExpiryDate() < System.currentTimeMillis()) {
            throw new RuntimeException("Refresh Token이 유효하지 않습니다.");
        }

        String newAccessToken = jwtTokenProvider.generateAccessToken(jwtTokenProvider.getUserIdFromToken(refreshToken));
        return ResponseEntity.ok(new TokenResponse(newAccessToken, refreshToken));
    }

    @PostMapping("/logout")
    public ResponseEntity<String> logout(HttpServletRequest request, HttpServletResponse response) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return ResponseEntity.status(401).body("토큰이 없습니다.");
        }

        String token = header.substring(7);
        if (!jwtTokenProvider.validateToken(token)) {
            return ResponseEntity.status(401).body("유효하지 않은 토큰입니다.");
        }

        String userId = jwtTokenProvider.getUserIdFromToken(token);
        Member member = memberMapper.selectByUserId(userId); // DB에서 회원 조회

        if (member != null) {
            memberService.logout(member.getId()); // Refresh Token 삭제
        }

        // Refresh Token 쿠키 삭제
        ResponseCookie cookie = ResponseCookie.from("refresh_token", "")
                .httpOnly(true)
                .secure(false)
                .path("/")
                .maxAge(0)
                .build();
        response.addHeader("Set-Cookie", cookie.toString());

        return ResponseEntity.ok("로그아웃 완료");
    }


    private void addRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        ResponseCookie cookie = ResponseCookie.from("refresh_token", refreshToken)
                .httpOnly(true)
                .secure(false)
                .path("/")
                .maxAge(jwtTokenProvider.getRefreshTokenExpiry() / 1000)
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }
}
