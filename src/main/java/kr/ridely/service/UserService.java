package kr.ridely.service;

import kr.ridely.dto.user.UserResponseDTO;
import kr.ridely.dto.user.UserUpdateRequestDTO;

/**
 * 회원 정보 서비스.
 *
 * 회원 번호는 요청 파라미터가 아니라 인증 정보(토큰)에서 얻는다.
 * 클라이언트가 보낸 값을 쓰면 남의 정보를 조회·수정할 수 있게 된다.
 */
public interface UserService {

    /**
     * 내 정보 조회.
     *
     * @throws kr.ridely.common.BusinessException COMMON-004 (계정 없음)
     */
    UserResponseDTO findById(long userId);

    /**
     * 내 정보 수정. 요청에 담긴 항목만 갱신한다.
     *
     * @return 수정 후 회원 정보
     * @throws kr.ridely.common.BusinessException COMMON-004 (계정 없음)
     */
    UserResponseDTO updateProfile(long userId, UserUpdateRequestDTO request);
}
