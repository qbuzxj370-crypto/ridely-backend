package kr.ridely.dto.savedRoute;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 저장한 코스의 이름·메모·즐겨찾기를 수정하는 요청.
 * PATCH /api/v1/saved-routes/{id} 의 요청 본문. (로그인 필요)
 *
 * 대응 테이블: saved_route (UPDATE)
 *
 * ※ PATCH이므로 보낸 항목만 수정된다. 보내지 않은 필드는 null로 들어오고
 *   서비스에서 건드리지 않는다.
 *
 * ※ 어떤 추천 코스를 가리키는지(recommendedRouteId)는 바꿀 수 없다.
 *   다른 코스를 저장하고 싶으면 새로 저장하면 된다.
 *
 * 요청 예시 (즐겨찾기만 켜기):
 * {
 *   "isFavorite": true
 * }
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SavedRouteUpdateRequestDTO {

    /** 바꿀 코스 이름. 보내지 않으면 기존 값 유지 */
    @Size(max = 100, message = "코스 이름은 100자 이하여야 합니다")
    private String customName;

    /** 바꿀 메모. 보내지 않으면 기존 값 유지 */
    private String memo;

    /**
     * 즐겨찾기 여부.
     * 래퍼 타입(Boolean)인 이유: 값을 안 보냈을 때 null로 구분하기 위해서다.
     * boolean(소문자)이면 안 보냈을 때 false가 되어 '즐겨찾기 해제'로 오해된다.
     */
    private Boolean isFavorite;
}
