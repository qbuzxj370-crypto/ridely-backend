package kr.ridely.service;

import kr.ridely.common.BusinessException;
import kr.ridely.common.ErrorCode;
import kr.ridely.dao.AuthDao;
import kr.ridely.dto.auth.SignupRequestDTO;
import kr.ridely.dto.user.UserResponseDTO;
import kr.ridely.vo.AppUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private final AuthDao authDao;
    private final PasswordEncoder passwordEncoder;

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
