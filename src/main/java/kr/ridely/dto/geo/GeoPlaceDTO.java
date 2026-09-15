package kr.ridely.dto.geo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 장소 검색 결과 한 건 (GET /api/v1/geo/search).
 *
 * 화면이 목록에서 하나를 고르면 {@code lat}·{@code lng}를 그대로 추천 요청의 출발지에 실으면 된다.
 *
 * 외부 응답을 그대로 내보내지 않고 이 형태로 줄인 이유는 둘이다. 카테고리 코드나 검색어 거리 같은 필드가 화면에서 쓰이지 않고, 좌표가 문자열로 오는 것을 클라이언트마다 각자 변환하게 두면 실수가 흩어진다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GeoPlaceDTO {

    /** 장소명. 예) 여의도한강공원 */
    private String placeName;

    /** 도로명 주소. 없으면 지번 주소가 들어온다 */
    private String address;

    /** 위도 */
    private double lat;

    /** 경도 */
    private double lng;

    /**
     * 서비스 지역 안인지.
     *
     * <b>밖이어도 목록에서 빼지 않고 표시만 한다.</b> 검색 결과에서 조용히 사라지면 사용자는 장소 이름을 잘못 쳤다고 생각하고 계속 다시 친다. 보여 주고 고를 수 없게 하는 편이 낫다.
     */
    private boolean inServiceArea;
}
