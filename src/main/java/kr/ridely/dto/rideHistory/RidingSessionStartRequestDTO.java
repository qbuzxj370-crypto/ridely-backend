package kr.ridely.dto.rideHistory;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 라이딩 시작 요청.
 * POST /api/v1/riding-sessions 의 요청 본문. (로그인 필요)
 *
 * 대응 테이블: riding_session (INSERT)
 *
 * ※ 시작 시각(started_at)은 클라이언트가 보내지 않고 서버가 NOW()로 찍는다.
 *   기기 시계가 틀어져 있을 수 있기 때문이다.
 *
 * ※ 이 시점에는 ended_at이 null이라 "진행 중인 세션"으로 취급된다.
 *   라이딩이 끝나면 PATCH /riding-sessions/{id} 로 마무리한다.
 *
 * 요청 예시 (추천 코스를 따라 달리는 경우):
 * {
 *   "recommendedRouteId": 42
 * }
 *
 * 요청 예시 (추천 없이 자유 주행):
 * {
 *   "recommendedRouteId": null
 * }
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RidingSessionStartRequestDTO {

    /**
     * 따라 달릴 추천 코스 번호. 선택 사항.
     * null이면 추천 코스 없이 자유롭게 달리는 라이딩으로 기록된다.
     * (이 경우 완주율 통계에서는 제외된다 — 완주할 목표 코스가 없으므로)
     */
    private Long recommendedRouteId;
}
