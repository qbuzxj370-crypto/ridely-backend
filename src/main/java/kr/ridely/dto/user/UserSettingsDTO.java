package kr.ridely.dto.user;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import lombok.*;

import java.math.BigDecimal;

/**
 * 사용자 환경설정.
 *
 * 아래 두 API에서 함께 사용한다 (필드 구성이 완전히 같기 때문):
 *   - GET   /api/v1/users/me/settings  (설정 조회 → 모든 필드가 채워져서 나감)
 *   - PATCH /api/v1/users/me/settings  (설정 수정 → 보낸 필드만 채워져서 들어옴)
 *
 * 대응 테이블: user_settings (회원당 1행)
 *
 * ※ 모든 필드를 래퍼 타입(Boolean, BigDecimal)으로 둔 이유:
 *   PATCH에서 "보내지 않은 항목"을 null로 구분하기 위해서다.
 *   boolean(소문자)이면 안 보냈을 때 자동으로 false가 되어 '끄기'로 오해된다.
 *
 * 요청 예시 (진동만 끄기):
 * {
 *   "vibrationEnabled": false
 * }
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserSettingsDTO {

    /**
     * 사고다발지역 회피 여부.
     * true면 코스를 짤 때 사고 잦은 구역을 우회한다 (ORS avoid_polygons 적용).
     * false면 회피하지 않고 지도에 표시 + 근접 알림만 준다.
     */
    private Boolean avoidDangerZones;

    /**
     * 코스 추천 기본 가중치 - 편의 (음수대·수리소·따릉이 경유 중시).
     * 기본값 0.50
     */
    @DecimalMin(value = "0.00", message = "가중치는 0 이상이어야 합니다")
    @DecimalMax(value = "1.00", message = "가중치는 1 이하여야 합니다")
    private BigDecimal defaultPriorityConvenience;

    /**
     * 코스 추천 기본 가중치 - 운동 (목표 거리·고도 중시).
     * 기본값 0.30
     */
    @DecimalMin(value = "0.00", message = "가중치는 0 이상이어야 합니다")
    @DecimalMax(value = "1.00", message = "가중치는 1 이하여야 합니다")
    private BigDecimal defaultPriorityExercise;

    /**
     * 코스 추천 기본 가중치 - 풍경·관광 (관광지·문화시설 경유 중시).
     * 기본값 0.20
     *
     * ※ 세 가중치의 합이 1.00이어야 한다.
     *   각 값이 0~1인지는 여기서 검사하지만,
     *   "합계 = 1.00" 검사는 세 값을 모두 봐야 하므로 서비스 레이어에서 처리한다.
     */
    @DecimalMin(value = "0.00", message = "가중치는 0 이상이어야 합니다")
    @DecimalMax(value = "1.00", message = "가중치는 1 이하여야 합니다")
    private BigDecimal defaultPriorityScenery;

    /**
     * 거리 표시 단위.
     * "km" 또는 "mile"
     */
    @Pattern(regexp = "km|mile", message = "단위는 km 또는 mile만 가능합니다")
    private String units;

    /** 라이딩 중 알림(관광지 접근, 사고다발지 경고) 수신 여부 */
    private Boolean notificationEnabled;

    /** 라이딩 중 진동 피드백 사용 여부 */
    private Boolean vibrationEnabled;
}
