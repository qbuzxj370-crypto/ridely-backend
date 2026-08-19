package kr.ridely.common;

/**
 * 좌표 간 거리 계산.
 *
 * PostGIS를 거치지 않고 자바에서 재는 경우가 세 곳으로 늘어 공통으로 뺐다. 자전거길 적재는 파트를 나눈 뒤에야 WKT를 만들 수 있어 DB에 넣기 전에 판정이 끝나야 하고, 후보 수집과 코스 추천은 좌표가 이미 메모리에 있어 왕복할 이유가 없다.
 *
 * 반지름은 WGS84 평균 반지름(6,371,008.8m)이다. 이 값으로 SCHEMA_CHANGE_ROUTE_GEOM.md의 간격 분석을 했으므로 문서의 수치와 직접 대조된다. 바꾸면 그 근거가 어긋난다.
 */
public final class GeoDistance {

    /** WGS84 평균 반지름(m) */
    private static final double EARTH_RADIUS_M = 6371008.8;

    private GeoDistance() {
    }

    /**
     * 두 좌표 사이의 대권 거리(m).
     *
     * 하버사인 공식이다. 수 km 규모에서 오차가 미터 이하라 우리 용도에는 충분하다.
     */
    public static double haversineM(double lng1, double lat1, double lng2, double lat2) {
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double dPhi = phi2 - phi1;
        double dLambda = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dPhi / 2) * Math.sin(dPhi / 2)
                + Math.cos(phi1) * Math.cos(phi2) * Math.sin(dLambda / 2) * Math.sin(dLambda / 2);
        return 2 * EARTH_RADIUS_M * Math.asin(Math.sqrt(a));
    }
}
