package kr.ridely.service;

import kr.ridely.common.BusinessException;
import kr.ridely.common.ErrorCode;
import kr.ridely.common.util.TokenHasher;
import kr.ridely.config.JwtProperties;
import kr.ridely.config.JwtTokenProvider;
import kr.ridely.dao.AuthDao;
import kr.ridely.dto.auth.LoginRequestDTO;
import kr.ridely.dto.auth.LoginResponseDTO;
import kr.ridely.dto.auth.SignupRequestDTO;
import kr.ridely.dto.user.UserResponseDTO;
import kr.ridely.vo.AppUser;
import kr.ridely.vo.RefreshToken;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.regex.Pattern;

/**
 * 회원가입 구현.
 *
 * 검증 책임 분리:
 *   - 형식 검증(빈값·길이·이메일 형식) → DTO @Valid → COMMON-001
 *   - 비밀번호 "정책" 검증(8~30자, 영문+숫자+특수문자 각 1자 이상)
 *     → 이 서비스에서 수행 → AUTH-102
 *   (DTO @Pattern으로 정책을 검증하면 전부 COMMON-001로 변환되어
 *    AUTH-102가 영영 나갈 수 없으므로, 정책 검증은 반드시 서비스 계층에 둔다)
 *
 * BCrypt 해시도 서비스 계층 책임 (핸드오프 §7 규칙).
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    /** 비밀번호 정책: 8~30자, 영문·숫자·특수문자 각 1자 이상 (위반 시 AUTH-102) */
    private static final Pattern PASSWORD_POLICY =
            Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,30}$");

    /** 계정 상태 — 이 값이 아니면 로그인할 수 없다 */
    private static final String STATUS_ACTIVE = "ACTIVE";

    private final AuthDao authDao;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;

    @Override
    @Transactional
    public UserResponseDTO signup(SignupRequestDTO request) {

        // 1. 비밀번호 정책 검증 → AUTH-102
        if (!PASSWORD_POLICY.matcher(request.getPassword()).matches()) {
            throw new BusinessException(ErrorCode.AUTH_102);
        }

        // 2. loginId 중복 검사 → AUTH-101
        //    (동시 가입 race는 app_user.login_id UNIQUE 제약이 최종 방어)
        if (authDao.existsByLoginId(request.getLoginId()) > 0) {
            throw new BusinessException(ErrorCode.AUTH_101);
        }

        // 3. BCrypt 해시 후 app_user INSERT. 평문은 절대 저장하지 않는다.
        AppUser user = new AppUser();
        user.setLoginId(request.getLoginId());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname());
        user.setEmail(request.getEmail());
        authDao.insertUser(user); // useGeneratedKeys → user.userId 채워짐

        // 4. user_settings 기본값 1행 (같은 트랜잭션 — 실패 시 둘 다 롤백)
        authDao.insertDefaultSettings(user.getUserId());

        // 5. DB 확정값(status='ACTIVE', created_at=NOW())을 재조회해 응답 구성
        AppUser saved = authDao.selectByLoginId(request.getLoginId());
        return toResponse(saved);
    }

    @Override
    @Transactional
    public LoginResponseDTO login(LoginRequestDTO request) {

        AppUser user = authDao.selectByLoginId(request.getLoginId());

        /*
         * 아이디가 없는 경우와 비밀번호가 틀린 경우를 같은 에러로 응답한다.
         * 구분해서 알려주면 "이 아이디는 가입돼 있다"는 정보가 새어 나가
         * 공격자가 유효한 아이디 목록을 수집할 수 있다.
         */
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.AUTH_201);
        }

        // 탈퇴·정지 계정은 자격 증명이 맞아도 로그인시키지 않는다
        if (!STATUS_ACTIVE.equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.AUTH_202);
        }

        long userId = user.getUserId();
        String accessToken = jwtTokenProvider.generateAccessToken(userId);
        String refreshToken = jwtTokenProvider.generateRefreshToken(userId);

        saveRefreshToken(userId, refreshToken);
        authDao.updateLastLoginAt(userId);

        return new LoginResponseDTO(accessToken, refreshToken,
                jwtProperties.accessTokenValiditySeconds());
    }

    @Override
    @Transactional
    public LoginResponseDTO refresh(String refreshToken) {

        /*
         * 세 겹으로 확인한다.
         *   1) 토큰 자체가 우리가 서명한 것이고 만료되지 않았는가 (JWT 검증)
         *   2) access 토큰을 재발급에 쓰려는 것은 아닌가 (type 클레임)
         *   3) 우리가 발급한 기록이 있고 아직 폐기되지 않았는가 (DB)
         *
         * 2)가 없으면 access 토큰으로도 재발급이 되어, 짧은 수명으로 피해를 줄이려던
         * 설계가 무의미해진다. 두 토큰이 같은 키로 서명되기 때문이다.
         */
        if (!jwtTokenProvider.validate(refreshToken)) {
            throw new BusinessException(ErrorCode.AUTH_302);
        }
        if (!JwtTokenProvider.TYPE_REFRESH.equals(jwtTokenProvider.getTokenType(refreshToken))) {
            throw new BusinessException(ErrorCode.AUTH_302);
        }

        String tokenHash = TokenHasher.sha256(refreshToken);
        RefreshToken stored = authDao.selectRefreshTokenByHash(tokenHash);

        // 발급 기록이 없거나(위조) 이미 폐기된 토큰(재사용 시도)
        if (stored == null || stored.getRevokedAt() != null) {
            throw new BusinessException(ErrorCode.AUTH_302);
        }
        // JWT 자체는 유효해도 DB 기록상 만료된 경우 (설정 변경 등으로 어긋날 수 있다)
        if (stored.getExpiresAt() != null && stored.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new BusinessException(ErrorCode.AUTH_302);
        }

        long userId = stored.getUserId();
        AppUser user = authDao.selectByUserId(userId);
        if (user == null || !STATUS_ACTIVE.equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.AUTH_202);
        }

        // 회전: 쓴 토큰은 폐기하고 새 쌍을 발급한다.
        // 폐기하지 않으면 탈취된 토큰이 만료까지 계속 유효하다.
        authDao.revokeRefreshToken(tokenHash);

        String newAccessToken = jwtTokenProvider.generateAccessToken(userId);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(userId);
        saveRefreshToken(userId, newRefreshToken);

        return new LoginResponseDTO(newAccessToken, newRefreshToken,
                jwtProperties.accessTokenValiditySeconds());
    }

    @Override
    @Transactional
    public void logout(String refreshToken) {
        // 토큰이 유효하지 않아도 오류로 만들지 않는다.
        // 목적은 "이 토큰을 못 쓰게 하는 것"이고, 그 상태는 이미 달성돼 있다.
        authDao.revokeRefreshToken(TokenHasher.sha256(refreshToken));
    }

    /**
     * 발급한 리프레시 토큰을 기록한다.
     *
     * 토큰 원문이 아니라 해시를 저장하므로, DB만 가지고는 인증할 수 없다.
     * 만료 시각을 함께 남겨 재발급 시 유효성을 판단한다.
     */
    private void saveRefreshToken(long userId, String refreshToken) {
        RefreshToken entity = new RefreshToken();
        entity.setUserId(userId);
        entity.setTokenHash(TokenHasher.sha256(refreshToken));
        entity.setExpiresAt(OffsetDateTime.now()
                .plusSeconds(jwtProperties.refreshTokenValiditySeconds()));
        authDao.insertRefreshToken(entity);
    }

    /** vo → 응답 DTO 변환. password_hash는 DTO에 필드가 없어 구조적으로 제외된다. */
    private UserResponseDTO toResponse(AppUser user) {
        return new UserResponseDTO(
                user.getUserId(),
                user.getLoginId(),
                user.getNickname(),
                user.getEmail(),
                user.getStatus(),
                user.getLastLoginAt(),
                user.getCreatedAt()
        );
    }
}
