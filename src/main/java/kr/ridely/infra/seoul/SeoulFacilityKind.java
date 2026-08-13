package kr.ridely.infra.seoul;

/**
 * 서울시 자전거 편의시설 종류.
 * 판정 규칙과 근거는 {@link SeoulFacilityClassifier} 참조.
 */
public enum SeoulFacilityKind {

    /** 공기주입기 → route_facility (facility_type = AIR_PUMP). 약 1,100건 */
    AIR_PUMP,

    /** 수리센터 → repair_shop. 약 23건 */
    REPAIR_SHOP,

    /**
     * 거치대·보관대 등. <b>적재하지 않는다.</b>
     *
     * 약 1,910건이 여기 해당하는데, bike_parking의 소스는
     * 행안부 자전거보관소정보 조회서비스(서울 2,698건)로 확정돼 있어
     * 함께 넣으면 중복 소스가 된다 — docs/shared/DATA_SOURCES.md 2.6
     */
    OTHER
}
