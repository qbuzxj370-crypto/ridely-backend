package kr.ridely.service;

import kr.ridely.dto.user.UserSettingsDTO;

/**
 * 회원 설정 서비스.
 *
 * 회원 번호는 요청 파라미터가 아니라 인증 정보(토큰)에서 얻는다.
 * 클라이언트가 보낸 값을 쓰면 남의 설정을 조회·수정할 수 있게 된다.
 *
 * UserService와 나눈 이유는 설정을 읽는 쪽이 회원 도메인 밖에 있어서다.
 * 코스 추천이 회피 설정을 읽고, 앞으로 기본 우선순위도 읽는다.
 * 그쪽이 회원 정보 조회·수정 서비스를 의존하게 두는 것보다 설정 서비스를 따로 두는 편이 낫다.
 */
public interface UserSettingsService {

    /**
     * 설정 조회.
     *
     * @throws kr.ridely.common.BusinessException COMMON-004 (설정 행 없음)
     */
    UserSettingsDTO findByUserId(long userId);

    /**
     * 설정 수정. 요청에 담긴 항목만 갱신한다.
     *
     * 우선순위 세 값은 묶음이다. 하나만 바꾸면 나머지는 기존 값이 남아 합이 1을 벗어나므로,
     * 셋 중 하나라도 보내면 셋 다 보내야 하고 합이 1이어야 한다.
     *
     * @return 수정 후 전체 설정
     * @throws kr.ridely.common.BusinessException COMMON-004 (설정 행 없음), ROUTE-001 (우선순위가 묶음이 아니거나 합이 1이 아님)
     */
    UserSettingsDTO update(long userId, UserSettingsDTO request);

    /**
     * 사고다발지 회피를 요청했는지 판정한다.
     *
     * 회원은 저장된 설정을, 비회원은 요청 헤더를 본다.
     * 회원이 헤더를 함께 보내도 설정이 이긴다 - 설정 화면에서 끈 것을 헤더로 되살릴 수 있으면 설정의 의미가 없다.
     *
     * 조회·수정과 달리 설정 행이 없어도 예외를 던지지 않는다. 코스 추천은 설정을 못 읽었다고 멈출 일이 아니라서,
     * 경고만 남기고 회피를 끈 것으로 본다.
     *
     * @param userId      로그인 회원 번호. 비회원이면 null
     * @param avoidHeader 비회원의 회피 요청 헤더값. 회원에게는 무시된다
     */
    boolean isAvoidRequested(Long userId, Boolean avoidHeader);
}
