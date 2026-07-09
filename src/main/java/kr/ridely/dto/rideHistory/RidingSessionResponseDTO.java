package kr.ridely.dto.rideHistory;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 라이딩 세션 응답.
 *
 * 아래 API들의 응답으로 함께 사용한다:
 *   - POST  /api/v1/riding-sessions       (시작 직후 — 아직 안 끝나서 대부분 null)
 *   - PATCH /api/v1/riding-sessions/{id}  (종료 직후 — 모든 값이 채워짐)
 *   - GET   /api/v1/riding-sessions       (내 라이딩 기록 목록)
 *
 * 대응 테이블: riding_session
 *
 * ※ 시작 직후에는 endedAt·distanceKm 등이 아직 null이다.
 *   endedAt이 null이면 "달리는 중"이라는 뜻이다.
 *
 * 응답 예시 (종료 후):
 * {
 *   "ridingSessionId": 8,
 *   "recommendedRouteId": 42,
 *   "startedAt": "2026-07-09T09:00:00+09:00",
 *   "endedAt": "2026-07-09T09:54:00+09:00",
 *   "distanceKm": 14.6,
 *   "avgSpeedKmh": 16.2,
 *   "isCompleted": true,
 *   "visitedPoiCount": 2,
 *   "alertReceivedCount": 1
 * }
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RidingSessionResponseDTO {

    /** 라이딩 세션 고유 번호 */
    private Long ridingSessionId;

    /**
     * 따라 달린 추천 코스 번호.
     * 자유 주행이었다면 null.
     */
    private Long recommendedRouteId;

    /** 라이딩 시작 시각 */
    private OffsetDateTime startedAt;

    /**
     * 라이딩 종료 시각.
     * null이면 아직 달리는 중이라는 뜻이다.
     */
    private OffsetDateTime endedAt;

    /** 실제 주행 거리(km). 종료 전에는 null */
    private BigDecimal distanceKm;

    /** 평균 속도(km/h). 종료 전에는 null */
    private BigDecimal avgSpeedKmh;

    /** 완주 여부. 종료 전에는 false */
    private boolean isCompleted;

    /** 방문한 관광지 수 */
    private int visitedPoiCount;

    /** 받은 사고다발지역 알림 횟수 */
    private int alertReceivedCount;

    /**
     * 실제 이동 경로 (GeoJSON LineString 문자열).
     * 목록 조회에서는 응답이 무거워지므로 담지 않는다 (null).
     * 단건 상세 조회에서만 채워진다.
     */
    private String trackGeoJson;
}
