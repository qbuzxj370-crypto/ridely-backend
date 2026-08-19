package kr.ridely.service;

import kr.ridely.dto.route.RouteRecommendRequestDTO;
import kr.ridely.dto.route.RouteRecommendResponseDTO;

/**
 * AI 코스 추천 오케스트레이터.
 *
 * 후보 수집부터 코치 코멘트까지를 한 흐름으로 잇는다.
 *
 *   1. 후보 수집        출발~도착 축 주변의 관광지·급수대·수리소·따릉이
 *   2. 코스 설계        LLM이 후보 중에서 경유지와 순서를 정한다
 *   3. 실재 확인        LLM이 만들어낸 번호를 걸러낸다
 *   4. 경로 탐색        ORS가 실제 도로를 따라 좌표를 그린다
 *   5. 강도 산출        실측 거리로 라벨을 매긴다
 *   6. 코멘트 생성      LLM이 트레이너 톤 해설을 쓴다
 *
 * 순서에 이유가 있다. 좌표는 LLM이 만들지 않고 라우팅 엔진이 만든다(4단계). 코멘트는 실제 거리가 나온 뒤에 쓴다(6단계) — 그래야 "목표보다 2km 길어졌다" 같은 말을 할 수 있다.
 *
 * 아직 없는 것
 *   - 사고다발지 통과 판정. passingDangerZones는 항상 비어 있다
 *   - 실패 시 fallback과 재호출. 어느 단계가 실패해도 COMMON-500이다
 *   - 같은 요청에 대한 캐시. 매번 LLM과 ORS를 부른다
 */
public interface RouteRecommendService {

    /**
     * 코스를 추천하고 저장한다.
     *
     * @param request 출발·도착·목표 거리·우선순위
     * @param userId  로그인 사용자 번호. 비회원이면 null
     * @return 경로 형상과 경유지, 코치 해설
     */
    RouteRecommendResponseDTO recommend(RouteRecommendRequestDTO request, Long userId);

    /**
     * 저장된 추천 코스를 다시 읽는다.
     *
     * @throws kr.ridely.common.BusinessException 없으면 COMMON-004
     */
    RouteRecommendResponseDTO findById(long recommendedRouteId);
}
