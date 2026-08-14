package kr.ridely.dto.route;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 코스 설계 후보 한 건.
 *
 * 관광지·급수대·수리소·따릉이는 서로 다른 테이블에서 오지만, LLM에게 넘길 때는 "종류·번호·이름·거리" 넉 줄이면 충분하다. 테이블별 DTO를 그대로 넘기면 쓰지 않는 필드(overview·thumbnailUrl 등)가 프롬프트 토큰을 잡아먹는다.
 *
 * LLM은 여기서 고른 것을 {type, id, reason} 형태로 돌려준다. 그 응답의 type·id가 이 클래스의 값과 그대로 대응해야 경유지를 되찾을 수 있다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CandidateDTO {

    /**
     * 후보 종류. TOUR(관광지) / WATER(급수대) / REPAIR_SHOP(수리소) / BIKE_STATION(따릉이)
     *
     * ⚠️ PoiItemDTO.type과 값이 다르다. 급수대는 저쪽에서 ROUTE_FACILITY + facilityType=WATER로 오는데, LLM 입장에서는 그 두 단계 구분이 의미가 없어 WATER로 평평하게 편다.
     */
    private String type;

    /** 원본 테이블에서의 고유 번호. type과 함께 봐야 유일하다 */
    private Long id;

    /** 표시 이름. LLM이 경유지 선정 이유를 쓸 때 쓴다 */
    private String name;

    /** 위도 */
    private double lat;

    /** 경도 */
    private double lng;

    /** 출발~도착 축에서 떨어진 거리(m). 후보 정렬 기준이다 */
    private Integer distanceM;

    /** 같은 후보인지 판정한다. LLM이 돌려준 (type, id)로 실재 여부를 거를 때 쓴다 */
    public boolean matches(String otherType, Long otherId) {
        return type != null && type.equals(otherType) && id != null && id.equals(otherId);
    }
}
