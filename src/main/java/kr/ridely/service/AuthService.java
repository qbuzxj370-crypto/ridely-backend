package kr.ridely.service;

import kr.ridely.dto.auth.LoginRequestDTO;
import kr.ridely.dto.auth.LoginResponseDTO;
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

    /**
     * 로그인.
     * 자격 증명을 확인하고 access·refresh 토큰을 발급한다.
     * refresh 토큰은 해시로 DB에 기록해 이후 재발급·폐기를 추적한다.
     *
     * @throws kr.ridely.common.BusinessException AUTH-201 (아이디 없음 또는 비밀번호 불일치),
     *                                            AUTH-202 (탈퇴·정지 계정)
     */
    LoginResponseDTO login(LoginRequestDTO request);

    /**
     * 토큰 재발급.
     *
     * 사용한 리프레시 토큰은 폐기하고 새 토큰 쌍을 발급한다(회전).
     * 폐기된 토큰이 다시 들어오면 탈취를 의심할 수 있으므로 거부한다.
     *
     * @throws kr.ridely.common.BusinessException AUTH-302 (만료·위조·폐기된 토큰),
     *                                            AUTH-202 (탈퇴·정지 계정)
     */
    LoginResponseDTO refresh(String refreshToken);

    /**
     * 로그아웃. 전달받은 리프레시 토큰을 폐기한다.
     *
     * 이미 폐기됐거나 존재하지 않는 토큰이어도 오류로 처리하지 않는다.
     * 로그아웃은 "이 토큰을 못 쓰게 한다"는 목적이고 그 상태는 이미 달성돼 있기 때문이다.
     */
    void logout(String refreshToken);
}
