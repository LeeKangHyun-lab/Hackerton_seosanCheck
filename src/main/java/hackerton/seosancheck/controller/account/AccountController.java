package hackerton.seosancheck.controller.account;

import hackerton.seosancheck.common.exception.CustomException;
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
import org.springframework.http.HttpStatus;
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
    public ResponseEntity<MemberResponse> join(@RequestBody MemberJoinRequest request, HttpServletResponse response) {
        if (request.getUserId() == null || request.getUserPw() == null || request.getNickname() == null) {
            throw new CustomException("아이디, 비밀번호, 닉네임은 필수입니다.", HttpStatus.BAD_REQUEST);
        }

        Member newMember = memberService.join(request);
        String accessToken = jwtTokenProvider.generateAccessToken(newMember.getUserId());
        String refreshToken = jwtTokenProvider.generateRefreshToken(newMember.getUserId());

        memberService.saveRefreshToken(newMember.getId(), refreshToken, jwtTokenProvider.getRefreshTokenExpiry());
        addRefreshTokenCookie(response, refreshToken);

        return ResponseEntity.ok(new MemberResponse(
                newMember.getId(),
                newMember.getUserId(),
                newMember.getNickname(),
                accessToken,
                refreshToken
        ));
    }


    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@RequestBody MemberLoginRequest request, HttpServletResponse response) {
        // 1. 입력값 검증
        if (request.getUserId() == null || request.getUserId().isBlank() ||
                request.getUserPw() == null || request.getUserPw().isBlank()) {
            throw new CustomException("아이디와 비밀번호는 필수입니다.", HttpStatus.BAD_REQUEST);
        }

        // 2. 로그인 시도
        Member loginMember = memberService.login(request);

        // 3. 토큰 발급
        String accessToken = jwtTokenProvider.generateAccessToken(loginMember.getUserId());
        String refreshToken = jwtTokenProvider.generateRefreshToken(loginMember.getUserId());

        // 4. Refresh Token DB 저장 및 쿠키 설정
        memberService.saveRefreshToken(loginMember.getId(), refreshToken, jwtTokenProvider.getRefreshTokenExpiry());
        addRefreshTokenCookie(response, refreshToken);

        return ResponseEntity.ok(new TokenResponse(accessToken, refreshToken));
    }


    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@CookieValue(value = "refresh_token", required = false) String refreshToken,
                                                 HttpServletResponse response) {
        if (refreshToken == null) {
            throw new CustomException("Refresh Token이 없습니다.", HttpStatus.UNAUTHORIZED);
        }

        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new CustomException("Refresh Token이 유효하지 않습니다.", HttpStatus.UNAUTHORIZED);
        }

        RefreshToken dbToken = refreshTokenMapper.findByToken(refreshToken);
        if (dbToken == null || dbToken.getExpiryDate() < System.currentTimeMillis()) {
            throw new CustomException("Refresh Token이 만료되었거나 존재하지 않습니다.", HttpStatus.UNAUTHORIZED);
        }

        String userId = jwtTokenProvider.getUserIdFromToken(refreshToken);
        String newAccessToken = jwtTokenProvider.generateAccessToken(userId);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(userId);

        memberService.saveRefreshToken(dbToken.getMemberId(), newRefreshToken, jwtTokenProvider.getRefreshTokenExpiry());
        addRefreshTokenCookie(response, newRefreshToken);

        return ResponseEntity.ok(new TokenResponse(newAccessToken, newRefreshToken));
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

    @DeleteMapping("/delete")
    public ResponseEntity<String> deleteAccount(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new CustomException("인증 토큰이 없습니다.", HttpStatus.UNAUTHORIZED);
        }

        String token = header.substring(7);
        if (!jwtTokenProvider.validateToken(token)) {
            throw new CustomException("유효하지 않은 토큰입니다.", HttpStatus.UNAUTHORIZED);
        }

        String userId = jwtTokenProvider.getUserIdFromToken(token);
        Member member = memberMapper.selectByUserId(userId);
        if (member == null) {
            throw new CustomException("회원 정보를 찾을 수 없습니다.", HttpStatus.NOT_FOUND);
        }

        memberService.softDeleteAccount(member.getId());

        return ResponseEntity.ok("탈퇴가 완료되었습니다. 30초 후 데이터가 완전 삭제됩니다.");
    }


    private void addRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        ResponseCookie cookie = ResponseCookie.from("refresh_token", refreshToken)
                .httpOnly(true)
                .secure(true) // HTTPS 환경에서만
                .sameSite("Strict") // CSRF 방어
                .path("/")
                .maxAge(jwtTokenProvider.getRefreshTokenExpiry() / 1000)
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

}
