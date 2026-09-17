package kr.ridely.service;

import kr.ridely.common.BusinessException;
import kr.ridely.common.ErrorCode;
import kr.ridely.common.PasswordPolicy;
import kr.ridely.dao.AuthDao;
import kr.ridely.dao.UserDao;
import kr.ridely.dto.user.PasswordChangeRequestDTO;
import kr.ridely.dto.user.UserResponseDTO;
import kr.ridely.dto.user.UserUpdateRequestDTO;
import kr.ridely.dto.user.WithdrawRequestDTO;
import kr.ridely.vo.AppUser;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 정보 조회·수정·탈퇴 구현.
 *
 * <h3>비밀번호를 다시 받는 두 곳</h3>
 *
 * 비밀번호 변경과 탈퇴는 토큰이 있어도 현재 비밀번호를 다시 확인한다. 토큰은 「이 기기가 로그인돼 있다」는 뜻이지 「지금 만지는 사람이 본인이다」는 뜻이 아니다. 잠깐 넘긴 기기에서 계정이 넘어가거나 사라지는 것을 막는다.
 *
 * 틀렸을 때는 로그인과 같은 {@code AUTH-201}이다. 새 코드를 만들 이유가 없다.
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    private final UserDao userDao;
    private final AuthDao authDao;
    private final PasswordEncoder passwordEncoder;

    @Override
    public UserResponseDTO findById(long userId) {
        return toResponse(getExistingUser(userId));
    }

    @Override
    @Transactional
    public UserResponseDTO updateProfile(long userId, UserUpdateRequestDTO request) {
        // 존재 확인을 먼저 한다. 없는 회원에 UPDATE를 실행하면 0행이 갱신되는데,
        // 그것만으로는 "계정이 없어서"인지 "바꿀 항목이 없어서"인지 구분할 수 없다.
        getExistingUser(userId);

        userDao.updateProfile(userId, request);

        // 갱신된 값(updated_at 포함)을 다시 읽어 응답한다
        return toResponse(getExistingUser(userId));
    }

    /**
     * 비밀번호를 바꾸고 <b>모든 리프레시 토큰을 폐기한다.</b>
     *
     * 폐기하는 이유는 비밀번호를 바꾸는 동기가 대개 「유출된 것 같다」이기 때문이다. 다른 기기의 세션을 살려 두면 바꾼 의미가 없다. 바꾼 기기도 함께 로그아웃되므로 화면은 변경 뒤 로그인 화면으로 보내야 한다.
     *
     * 새 비밀번호가 현재와 같아도 막지 않는다. 막으면 「현재 비밀번호가 무엇인지」를 응답이 알려주는 셈이 된다.
     */
    @Override
    @Transactional
    public void changePassword(long userId, PasswordChangeRequestDTO request) {
        AppUser user = getExistingUser(userId);
        verifyPassword(request.getCurrentPassword(), user);

        if (!PasswordPolicy.isValid(request.getNewPassword())) {
            throw new BusinessException(ErrorCode.AUTH_102);
        }

        userDao.updatePassword(userId, passwordEncoder.encode(request.getNewPassword()));
        int revoked = authDao.revokeAllRefreshTokens(userId);

        log.info("비밀번호를 바꿨다. 리프레시 토큰 {}건 폐기: userId={}", revoked, userId);
    }

    /**
     * 계정을 지운다.
     *
     * 행 하나를 지우면 FK가 나머지를 처리한다. 설정·저장 경로·라이딩 세션·토큰은 CASCADE로 함께 사라지고, 추천 코스는 SET NULL로 남는다. 코스는 인증 없이 조회되는 공용 자산이라 개인과의 연결만 끊으면 된다.
     *
     * ⚠️ {@code recommendation_cache}는 FK가 없어 남는다. 키가 해시라 역추적이 안 되고 TTL 20분이라 곧 사라지지만, 탈퇴 직후 20분 동안은 출발지 좌표가 들어 있는 행이 존재한다.
     */
    @Override
    @Transactional
    public void withdraw(long userId, WithdrawRequestDTO request) {
        AppUser user = getExistingUser(userId);
        verifyPassword(request.getPassword(), user);

        userDao.withdraw(userId);

        // 비밀번호 확인을 통과했으니 본인이다. 감사 목적으로 번호만 남긴다 - 아이디는 이미 지워졌다
        log.info("회원이 탈퇴했다: userId={}", userId);
    }

    /**
     * 비밀번호 대조. 틀리면 로그인과 같은 AUTH-201이다.
     *
     * 이 메서드에 오기 전에 계정 존재를 이미 확인했으므로 「아이디가 없다」와 섞일 일이 없다.
     */
    private void verifyPassword(String rawPassword, AppUser user) {
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.AUTH_201);
        }
    }

    /**
     * 회원 조회. 없으면 COMMON-004.
     *
     * 토큰은 유효한데 계정이 사라진 경우(관리자 삭제 등)에 해당한다.
     * 흔치 않지만 그대로 두면 NullPointerException으로 500이 나간다.
     */
    private AppUser getExistingUser(long userId) {
        AppUser user = userDao.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.COMMON_004);
        }
        return user;
    }

    /** vo → 응답 DTO. password_hash는 DTO에 필드가 없어 구조적으로 제외된다 */
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
