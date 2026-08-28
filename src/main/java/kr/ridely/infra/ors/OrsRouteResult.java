package kr.ridely.infra.ors;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * ORS 응답에서 우리가 쓰는 값만 추린 결과.
 *
 * 오케스트레이터(RouteRecommendService)와 적재(RouteDao)가 이걸 받는다. 원본 응답 구조(FeatureCollection 중첩)를 서비스 계층까지 끌고 가지 않으려고 한 겹 벗긴다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrsRouteResult {

    /**
     * 경로 형상 (GeoJSON LineString 문자열).
     *
     * ⚠️ elevation=true로 호출하므로 좌표가 3개 값([경도, 위도, 고도])이다. route_geom 컬럼은 2D라 적재할 때 ST_Force2D로 Z를 떨궈야 한다.
     */
    private String geometryGeoJson;

    /** 좌표 점 개수. 응답이 통째로 잘렸는지 눈으로 확인하는 용도 */
    private int pointCount;

    /** 총 거리(m) */
    private double distanceM;

    /** 예상 소요 시간(초). ORS 자전거 속도 모델 기준 */
    private double durationSec;

    /**
     * 누적 오르막(m).
     *
     * ⚠️ 값을 그대로 믿지 않는다. ORS는 양의 고도 변화를 필터 없이 전부 더한다. 실측에서 표고차 31m짜리 한강 평지 구간에 상승 137.9m가 나왔고, 그중 68.6m가 경사 20%를 넘는(자전거로 오를 수 없는) 구간에서 발생했다. 원인은 좌표 간격(중앙값 33m)이 SRTM 격자(30~90m)와 겹쳐 점마다 다른 셀의 오차를 집는 것이다 — 근거는 docs/shared/SCHEMA_CHANGE_POI.md 6.5.
     *
     * 그래서 표시용으로만 쓰고 강도 판정에는 넣지 않는다. 보정 방법을 여섯 구간 실측으로 찾아봤으나 경사 상한·리샘플링·국토지리정보원 DEM이 전부 막혔다. 정답을 모르는 것이 근본 원인이라 <b>지금은 보정하지 않는다</b> - docs/shared/SPIKE_ELEVATION.md.
     */
    private double ascentM;

    /** 누적 내리막(m). ascentM과 같다 */
    private double descentM;

    /** 총 거리(km). 소수 셋째 자리까지 — recommended_route.total_distance_km이 NUMERIC(7,3)이다 */
    public double distanceKm() {
        return Math.round(distanceM / 1000.0 * 1000.0) / 1000.0;
    }

    /** 예상 소요 시간(분), 올림. 0분으로 표시되지 않게 최소 1분을 보장한다 */
    public int durationMin() {
        return Math.max(1, (int) Math.ceil(durationSec / 60.0));
    }
}
