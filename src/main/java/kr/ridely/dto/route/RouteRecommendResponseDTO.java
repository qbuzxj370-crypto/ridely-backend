package kr.ridely.dto.route;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * AI 코스 추천 응답.
 *
 * 아래 두 API의 응답으로 함께 사용한다:
 *   - POST /api/v1/routes/recommend  (새 코스 추천)
 *   - GET  /api/v1/routes/{id}       (이전에 추천받은 코스 다시 보기)
 *
 * 대응 테이블: recommended_route
 *
 * ※ ai_ 로 시작하는 필드들은 LLM(Coach Ridely)이 생성한 자연어 문장이다.
 *   앱에서는 이 문장을 코치 말풍선으로 보여 준다.
 *
 * 응답 예시 (일부 생략):
 * {
 *   "recommendedRouteId": 42,
 *   "totalDistanceKm": 14.8,
 *   "estimatedDurationMin": 58,
 *   "intensityLevel": "MODERATE",
 *   "routeGeoJson": "{\"type\":\"LineString\",\"coordinates\":[...]}",
 *   "waypoints": [ ... ],
 *   "passingDangerZones": [ ... ],
 *   "aiTitle": "한강 따라 14km, 적당히 땀 빼는 코스",
 *   "aiCoachComment": "오늘은 14km 가볍게 풀어봐요. 8km쯤에 선유도공원 한 번 들르고..."
 * }
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RouteRecommendResponseDTO {

    // ===== 코스 식별 =====

    /** 추천 코스 고유 번호. 저장하거나 다시 조회할 때 쓴다 */
    private Long recommendedRouteId;

    /** 추천 생성 시각 */
    private OffsetDateTime createdAt;


    // ===== 경로 요약 =====

    /** 실제 총 주행 거리(km). 요청한 목표 거리와 약간 다를 수 있다 */
    private BigDecimal totalDistanceKm;

    /** 예상 소요 시간(분). 자전거 평균 속도 15~20km/h 기준 */
    private Integer estimatedDurationMin;

    /** 누적 오르막 고도(m) */
    private Integer totalAscentM;

    /** 누적 내리막 고도(m) */
    private Integer totalDescentM;

    /**
     * 운동 강도 라벨.
     * LIGHT(가볍게) / MODERATE(적당히) / HARD(끌어올림) / CHALLENGE(도전)
     * 거리와 누적 고도를 종합해 서버가 계산한다.
     */
    private String intensityLevel;

    /**
     * 실제 주행 경로 (GeoJSON LineString 문자열).
     * OpenRouteService가 실제 도로를 따라 만들어 준 좌표들이다.
     * 지도에 파란 선으로 그린다.
     *
     * ※ AI가 좌표를 직접 만들지 않는다. AI는 "어디를 들를지"만 정하고,
     *   실제 길찾기는 라우팅 엔진이 한다 (AI 환각으로 엉뚱한 좌표가 나오는 것 방지).
     */
    private String routeGeoJson;

    /** 사고다발지역 회피 기능이 실제로 적용됐는지 여부 */
    private Boolean avoidDangerZonesApplied;


    // ===== 경유지 / 위험구간 =====

    /** AI가 배치한 경유지 목록 (관광지·음수대·수리소 등) */
    private List<WaypointDTO> waypoints;

    /**
     * 이 코스가 통과하는 사고다발지역 목록.
     * 회피 설정을 켰다면 비어 있다.
     */
    private List<PassingDangerZoneDTO> passingDangerZones;


    // ===== AI 코치 코멘트 =====

    /** AI가 붙인 코스 제목. 예: "한강 따라 14km, 적당히 땀 빼는 코스" */
    private String aiTitle;

    /**
     * 코스의 핵심 포인트 목록.
     * 예: ["8km 지점 선유도공원 경유", "12km 지점 반포 급수대"]
     * 대응 컬럼: recommended_route.ai_highlights (JSONB 배열)
     */
    private List<String> aiHighlights;

    /** 코치의 한마디 (1~2문장). 앱 메인에 말풍선으로 보여 준다 */
    private String aiCoachComment;

    /**
     * 사고다발지역 통과 시의 주의 멘트.
     * 통과하는 위험 구간이 없으면 null.
     */
    private String aiDangerZoneAlert;

    /**
     * 다음 라이딩 제안. 예: "다음엔 16km 한 번 도전해봐요?"
     * 과거 라이딩 이력(RAG)이 있는 로그인 사용자에게만 제공된다.
     * 비회원이면 null.
     */
    private String aiNextStepSuggestion;
}
