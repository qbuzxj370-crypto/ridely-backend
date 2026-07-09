package kr.ridely.dto.savedRoute;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 저장한 코스 응답.
 *
 * 아래 API들의 응답으로 함께 사용한다:
 *   - POST  /api/v1/saved-routes       (저장 직후)
 *   - GET   /api/v1/saved-routes       (내 저장 코스 목록 — 이 DTO의 List)
 *   - GET   /api/v1/saved-routes/{id}  (단건 조회)
 *   - PATCH /api/v1/saved-routes/{id}  (수정 직후)
 *
 * 대응 테이블: saved_route + recommended_route (JOIN)
 *
 * ※ 목록 화면에서 코스 이름만 보여 주면 무슨 코스인지 알 수 없으므로,
 *   추천 코스의 요약 정보(거리·시간·강도)를 함께 조인해서 내려 준다.
 *   전체 경로 좌표가 필요하면 recommendedRouteId로 /routes/{id}를 따로 부른다.
 *
 * 응답 예시:
 * {
 *   "savedRouteId": 3,
 *   "recommendedRouteId": 42,
 *   "customName": "주말 한강 코스",
 *   "memo": "선유도공원 카페 들르기 좋음",
 *   "isFavorite": true,
 *   "createdAt": "2026-07-09T14:20:00+09:00",
 *   "aiTitle": "한강 따라 14km, 적당히 땀 빼는 코스",
 *   "totalDistanceKm": 14.8,
 *   "estimatedDurationMin": 58,
 *   "intensityLevel": "MODERATE"
 * }
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SavedRouteResponseDTO {

    // ===== saved_route 테이블에서 온 값 =====

    /** 저장 기록 고유 번호 */
    private Long savedRouteId;

    /** 어떤 추천 코스를 저장한 것인지. 상세 조회 시 이 번호를 쓴다 */
    private Long recommendedRouteId;

    /** 사용자가 붙인 이름. 안 붙였으면 null (이때 화면엔 aiTitle을 보여 준다) */
    private String customName;

    /** 사용자 메모 */
    private String memo;

    /** 즐겨찾기 여부 */
    private boolean isFavorite;

    /** 저장한 시각 */
    private OffsetDateTime createdAt;


    // ===== recommended_route 테이블에서 조인해 온 요약 정보 =====

    /** AI가 지어 준 코스 제목 */
    private String aiTitle;

    /** 총 거리(km) */
    private BigDecimal totalDistanceKm;

    /** 예상 소요 시간(분) */
    private Integer estimatedDurationMin;

    /** 운동 강도. LIGHT / MODERATE / HARD / CHALLENGE */
    private String intensityLevel;
}
