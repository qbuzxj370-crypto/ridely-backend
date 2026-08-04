package kr.ridely.service;

import kr.ridely.common.BusinessException;
import kr.ridely.common.ErrorCode;
import kr.ridely.dao.UserDao;
import kr.ridely.dto.user.UserResponseDTO;
import kr.ridely.dto.user.UserUpdateRequestDTO;
import kr.ridely.vo.AppUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 정보 조회·수정 구현.
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserDao userDao;

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
