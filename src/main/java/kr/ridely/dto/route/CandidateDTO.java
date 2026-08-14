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

    /** 출발~도착 축에서 수직으로 떨어진 거리(m). 후보 정렬 기준이다 */
    private Integer distanceM;

    /**
     * 축 위로 투영한 진행도. 0이 출발지, 1이 도착지다.
     *
     * distanceM이 "경로를 얼마나 벗어났나"라면 이건 "경로의 어느 지점인가"다. 둘은 다른 값이고 LLM에는 둘 다 필요하다. 진행도를 모르면 경유지를 순서대로 배치할 수 없고 "9km 지점 급수대" 같은 이유도 쓸 수 없다.
     *
     * 도착지가 없는 순환 코스는 축이 없으므로 null이다.
     */
    private Double progressRatio;

    /**
     * 출발지에서 축을 따라 몇 km 지점인지. 프롬프트에 이 값을 쓴다.
     *
     * 진행도(0~1)를 그대로 주면 LLM이 거리 감각을 못 잡는다. 목표 거리를 곱해 km로 바꿔야 "9km 지점"처럼 쓸 수 있다.
     *
     * @param targetDistanceKm 목표 주행 거리
     * @return 순환 코스처럼 진행도가 없으면 null
     */
    public Double progressKm(double targetDistanceKm) {
        return progressRatio == null ? null
                : Math.round(progressRatio * targetDistanceKm * 10) / 10.0;
    }

    /**
     * 진행도를 뺀 편의 생성자.
     *
     * 진행도는 조회 결과만으로는 계산할 수 없다. 출발지·도착지를 알아야 축에 투영할 수 있어서 수집기가 나중에 채운다. 그래서 생성 시점에는 넣을 값이 없다.
     */
    public CandidateDTO(String type, Long id, String name, double lat, double lng, Integer distanceM) {
        this.type = type;
        this.id = id;
        this.name = name;
        this.lat = lat;
        this.lng = lng;
        this.distanceM = distanceM;
    }

    /** 같은 후보인지 판정한다. LLM이 돌려준 (type, id)로 실재 여부를 거를 때 쓴다 */
    public boolean matches(String otherType, Long otherId) {
        return type != null && type.equals(otherType) && id != null && id.equals(otherId);
    }
}
