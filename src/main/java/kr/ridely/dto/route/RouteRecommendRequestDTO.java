package kr.ridely.dto.route;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * AI 코스 추천 요청.
 * POST /api/v1/routes/recommend 의 요청 본문.
 *
 * 대응 테이블: recommended_route (INSERT할 때 이 값들이 그대로 기록된다)
 *
 * ※ 로그인하지 않아도 호출할 수 있다. 다만 로그인 사용자만
 *   과거 라이딩 이력(RAG)을 반영한 개인화 추천을 받는다.
 *
 * ※ 사고다발지역 회피 여부(avoidDangerZones)는 여기서 받지 않는다.
 *   로그인 사용자는 user_settings 값을 서버가 읽어서 적용하고,
 *   비회원은 요청 헤더(X-Ridely-Avoid-Danger-Zones)로 전달한다.
 *
 * 요청 예시:
 * {
 *   "startLat": 37.5665, "startLng": 126.9780,
 *   "endLat": null, "endLng": null,
 *   "targetDistanceKm": 15.0,
 *   "priorityConvenience": 0.50,
 *   "priorityExercise": 0.30,
 *   "priorityScenery": 0.20
 * }
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RouteRecommendRequestDTO {

    /** 출발지 위도 (필수) */
    @NotNull(message = "출발지 위도를 입력해 주세요")
    private Double startLat;

    /** 출발지 경도 (필수) */
    @NotNull(message = "출발지 경도를 입력해 주세요")
    private Double startLng;

    /**
     * 도착지 위도. 선택 사항.
     * 보내지 않으면 "출발지로 되돌아오는 순환 코스"로 설계한다.
     */
    private Double endLat;

    /** 도착지 경도. 선택 사항 */
    private Double endLng;

    /**
     * 목표 주행 거리(km).
     * AI가 이 거리에 최대한 맞춰 경유지를 배치한다 (오차 ±10% 목표).
     */
    @NotNull(message = "목표 거리를 입력해 주세요")
    @DecimalMin(value = "1.0", message = "목표 거리는 1km 이상이어야 합니다")
    @DecimalMax(value = "100.0", message = "목표 거리는 100km 이하여야 합니다")
    private BigDecimal targetDistanceKm;

    /**
     * 가중치 - 편의 (음수대·수리소·따릉이를 자주 지나가게).
     * 보내지 않으면 user_settings의 기본값(0.50)을 사용한다.
     */
    @DecimalMin(value = "0.00") @DecimalMax(value = "1.00")
    private BigDecimal priorityConvenience;

    /**
     * 가중치 - 운동 (목표 거리·누적 고도를 중시).
     * 보내지 않으면 기본값 0.30.
     */
    @DecimalMin(value = "0.00") @DecimalMax(value = "1.00")
    private BigDecimal priorityExercise;

    /**
     * 가중치 - 풍경·관광 (관광지·문화시설을 경유하게).
     * 보내지 않으면 기본값 0.20.
     *
     * ※ 세 가중치의 합은 1.00이어야 한다. 합계 검증은 서비스 레이어에서 한다.
     */
    @DecimalMin(value = "0.00") @DecimalMax(value = "1.00")
    private BigDecimal priorityScenery;
}
