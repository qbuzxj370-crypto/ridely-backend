package kr.ridely.dto.savedRoute;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 추천받은 코스를 내 목록에 저장하는 요청.
 * POST /api/v1/saved-routes 의 요청 본문. (로그인 필요)
 *
 * 대응 테이블: saved_route (INSERT)
 *
 * ※ 코스 내용 자체는 recommended_route 테이블에 이미 저장돼 있다.
 *   여기서는 "누가 어떤 추천 코스를 저장했는지"만 연결한다.
 *
 * ※ 같은 코스를 두 번 저장할 수 없다 (DB의 UNIQUE(user_id, recommended_route_id) 제약).
 *
 * 요청 예시:
 * {
 *   "recommendedRouteId": 42,
 *   "customName": "주말 한강 코스",
 *   "memo": "선유도공원 카페 들르기 좋음"
 * }
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SavedRouteCreateRequestDTO {

    /** 저장할 추천 코스의 번호 (recommended_route.recommended_route_id) */
    @NotNull(message = "저장할 코스를 지정해 주세요")
    private Long recommendedRouteId;

    /**
     * 사용자가 직접 붙인 이름. 선택 사항.
     * 비워 두면 목록에서 AI가 지어 준 제목(ai_title)을 대신 보여 준다.
     */
    @Size(max = 100, message = "코스 이름은 100자 이하여야 합니다")
    private String customName;

    /** 메모. 선택 사항 */
    private String memo;
}
