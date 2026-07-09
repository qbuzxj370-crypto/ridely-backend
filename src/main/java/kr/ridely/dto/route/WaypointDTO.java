package kr.ridely.dto.route;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * AI가 코스에 배치한 경유지 하나.
 *
 * 대응 컬럼: recommended_route.waypoints_json (JSONB 배열의 요소 하나)
 *
 * ※ DB에는 JSON 문자열로 저장되고, 조회할 때 이 클래스로 변환한다.
 *   컬럼을 따로 만들지 않고 JSONB에 넣는 이유는 경유지 개수가 코스마다
 *   달라서(2~5개) 별도 테이블로 빼기엔 과하기 때문이다.
 *
 * ※ reason 필드가 이 서비스의 차별점이다.
 *   단순히 "여기를 지나갑니다"가 아니라 "왜 이곳을 골랐는지"를 AI가 설명한다.
 *
 * JSON 예시:
 * {
 *   "type": "TOUR_ATTRACTION",
 *   "id": 12,
 *   "name": "선유도공원",
 *   "lat": 37.5434, "lng": 126.8997,
 *   "distanceFromStartKm": 8.4,
 *   "reason": "8km 지점, 잠깐 쉬면서 한강 풍경 챙기기 좋아요"
 * }
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WaypointDTO {

    /**
     * 경유지 종류.
     * TOUR_ATTRACTION(관광지) / BIKE_STATION(따릉이) / REPAIR_SHOP(수리센터)
     * / ROUTE_FACILITY(급수대·화장실·인증센터) / BIKE_PARKING(보관소)
     *
     * 이 값에 따라 어느 테이블의 id인지가 결정된다.
     */
    private String type;

    /** 해당 테이블에서의 고유 번호 */
    private Long id;

    /** 경유지 이름 */
    private String name;

    /** 위도 */
    private double lat;

    /** 경도 */
    private double lng;

    /**
     * 출발지에서 이 지점까지의 누적 거리(km).
     * "9km 지점에 음수대가 있어요" 같은 안내에 쓴다.
     */
    private BigDecimal distanceFromStartKm;

    /**
     * AI가 이 경유지를 고른 이유 (Coach Ridely 말투).
     * 예: "9km쯤이면 물 떨어질 타이밍이에요"
     */
    private String reason;
}
