package kr.ridely.common.util;

import org.locationtech.jts.geom.Point;

/**
 * PostGIS geometry ↔ 좌표 변환 유틸.
 * JdbcClient로 조회한 결과의 geometry를 lat/lng로 다룰 때 사용.
 *
 * ADR-002: PostGIS 쿼리는 JdbcClient 영역.
 * 쿼리에서 ST_X(geom)·ST_Y(geom)로 분리 추출하면 이 유틸 없이도 되지만,
 * JTS Point 객체를 직접 받는 경우를 위해 헬퍼 제공.
 */
public final class GeometryUtils {

    private GeometryUtils() {
    }

    /** JTS Point → 위도 (Y) */
    public static double lat(Point point) {
        return point.getY();
    }

    /** JTS Point → 경도 (X) */
    public static double lng(Point point) {
        return point.getX();
    }

    /**
     * WKT 조립 헬퍼 (파라미터 바인딩 시 참고용).
     * 실제로는 SQL에서 ST_SetSRID(ST_MakePoint(:lng,:lat),4326) 사용 권장.
     */
    public static String toPointWkt(double lng, double lat) {
        return "POINT(" + lng + " " + lat + ")";
    }
}
