package kr.ridely.service;

import kr.ridely.dto.route.RouteRecommendRequestDTO;
import kr.ridely.dto.route.RouteRecommendResponseDTO;

/**
 * AI 코스 추천 오케스트레이터.
 *
 * 후보 수집부터 코치 코멘트까지를 한 흐름으로 잇는다.
 *
 *   0. 재현 확인        Idempotency-Key가 왔고 창 안에 같은 키가 있으면 저장된 응답으로 끝난다
 *   1. 후보 수집        출발~도착 축 주변의 관광지·급수대·수리소·따릉이
 *   2. 코스 설계        LLM이 후보 중에서 경유지와 순서를 정한다
 *   3. 실재 확인        LLM이 만들어낸 번호를 걸러낸다
 *   4. 경로 탐색        ORS가 실제 도로를 따라 좌표를 그린다. 회피 설정이 켜져 있으면 사고다발지를 지나지 않게 그린다
 *   5. 거리 보정        목표에 못 미치면 자전거도로 위 연장점을 끼우고 다시 잰다
 *   6. 통과 판정        확정된 형상이 지나는 사고다발지를 찾는다
 *   7. 강도 산출        실측 거리로 라벨을 매긴다
 *   8. 코멘트 생성      LLM이 트레이너 톤 해설을 쓴다
 *
 * 순서에 이유가 있다. 좌표는 LLM이 만들지 않고 라우팅 엔진이 만든다(4단계). 통과 판정은 보정이 끝난 뒤에 한다(6단계) - 연장 구간에서 새로 지나가게 된 구역을 놓치지 않기 위해서다. 코멘트는 거리와 통과 구역이 모두 확정된 뒤에 쓴다(8단계).
 *
 * <b>재현은 요청 조건이 아니라 키로 판정한다(0단계).</b> 조건으로 만들면 GPS 오차 때문에 같은 자리에서도 좌표가 흔들려 재시도가 새 요청으로 읽히고, 반대로 재추천은 조건이 같아 갇힌다. 저장 여부는 {@code RecommendationReplayStore}가 정한다 - 규칙으로 만든 응답과 회피에 실패한 응답은 남기지 않는다.
 *
 * 아직 없는 것
 *   - 외부 호출 실패의 구분. LLM 실패는 규칙 대체가 받지만 ORS가 실패하면 COMMON-500으로 나가 클라이언트가 우리 장애와 구분하지 못한다
 */
public interface RouteRecommendService {

    /**
     * 코스를 추천하고 저장한다.
     *
     * @param request     출발·도착·목표 거리·우선순위
     * @param userId      로그인 사용자 번호. 비회원이면 null
     * @param avoidHeader     비회원의 사고다발지 회피 요청. 회원은 user_settings를 읽으므로 무시한다
     * @param idempotencyKey  재시도 식별용. 같은 값이 창 안에 다시 오면 첫 응답을 그대로 돌려준다.
     *                        null이면 매번 새로 만든다
     * @return 경로 형상과 경유지, 코치 해설
     */
    RouteRecommendResponseDTO recommend(RouteRecommendRequestDTO request, Long userId,
                                        Boolean avoidHeader, String idempotencyKey);

    /**
     * 저장된 추천 코스를 다시 읽는다.
     *
     * @throws kr.ridely.common.BusinessException 없으면 COMMON-004
     */
    RouteRecommendResponseDTO findById(long recommendedRouteId);
}
