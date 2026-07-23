package kr.ridely.service;

import kr.ridely.dto.auth.SignupRequestDTO;
import kr.ridely.dto.user.UserResponseDTO;

/**
 * 인증 도메인 서비스 인터페이스.
 * 구현: AuthServiceImpl (구조 Z: 인터페이스 + Impl 쌍).
 */
public interface AuthService {

    /**
     * 회원가입.
     * app_user + user_settings(기본값) 1행씩을 한 트랜잭션으로 생성한다.
     *
     * @throws kr.ridely.common.BusinessException AUTH-102 (비밀번호 정책 위반),
     *                                            AUTH-101 (loginId 중복)
     * @return 생성된 회원 정보 (password_hash 미포함 — UserResponseDTO)
     */
    UserResponseDTO signup(SignupRequestDTO request);
}
