package kr.ridely.dto.geo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 장소 검색 응답 (GET /api/v1/geo/search).
 *
 * <b>결과가 없어도 200에 빈 배열이다.</b> 오타나 없는 장소를 친 것은 실패가 아니다. 외부 호출이 실제로 실패했을 때만 {@code GEO-001}(502)이 나간다 - 사용자가 다시 쳐야 하는 상황과 잠시 뒤 다시 시도해야 하는 상황이 갈린다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GeoSearchResponseDTO {

    /** 검색어 (요청값 그대로 반환) */
    private String query;

    /** 검색 결과. 없으면 빈 배열 */
    private List<GeoPlaceDTO> items;

    /** items의 개수 */
    private int totalCount;
}
