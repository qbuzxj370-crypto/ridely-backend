package kr.ridely.dto.rideHistory;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 라이딩 종료 요청.
 * PATCH /api/v1/riding-sessions/{id} 의 요청 본문. (로그인 필요)
 *
 * 대응 테이블: riding_session (UPDATE)
 *
 * ※ 종료 시각(ended_at)은 서버가 NOW()로 찍는다.
 *
 * ※ 라이딩 중 앱이 계속 모은 값들을 종료 시점에 한 번에 보낸다.
 *   (라이딩 도중 매초 서버에 보내면 배터리·통신량 낭비)
 *
 * ※ 이 데이터가 쌓여서 나중에 AI 개인화 추천의 재료가 된다.
 *   종료 처리 후 서버가 비동기로 riding_history_embedding에 벡터를 적재한다.
 *
 * 요청 예시:
 * {
 *   "distanceKm": 14.6,
 *   "avgSpeedKmh": 16.2,
 *   "isCompleted": true,
 *   "visitedPoiCount": 2,
 *   "alertReceivedCount": 1,
 *   "trackGeoJson": "{\"type\":\"LineString\",\"coordinates\":[...]}"
 * }
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RidingSessionEndRequestDTO {

    /** 실제로 달린 거리(km). GPS로 측정한 값 */
    @DecimalMin(value = "0.0", message = "거리는 0 이상이어야 합니다")
    private BigDecimal distanceKm;

    /** 평균 속도(km/h) */
    @DecimalMin(value = "0.0", message = "속도는 0 이상이어야 합니다")
    private BigDecimal avgSpeedKmh;

    /**
     * 완주 여부.
     * 추천 코스의 도착 지점 근처에 도달했는지를 앱이 판단해서 보낸다.
     * 이 값의 비율이 제안서의 '완주율' 지표가 된다.
     */
    private Boolean isCompleted;

    /**
     * 라이딩 중 방문한 관광지 수.
     * 관광지 200m 이내에 접근하면 방문으로 인정한다.
     */
    @Min(value = 0, message = "방문 수는 0 이상이어야 합니다")
    private Integer visitedPoiCount;

    /**
     * 라이딩 중 받은 사고다발지역 알림 횟수.
     * 안전 효과 측정 지표로 쓰인다.
     */
    @Min(value = 0, message = "알림 수는 0 이상이어야 합니다")
    private Integer alertReceivedCount;

    /**
     * 실제 이동 경로 (GeoJSON LineString 문자열). 선택 사항.
     * GPS 좌표를 일정 간격(예: 10초 또는 50m마다)으로 샘플링해 보낸다.
     * 매 좌표를 다 보내면 데이터가 너무 커지므로 앱에서 솎아 낸다.
     */
    private String trackGeoJson;
}
