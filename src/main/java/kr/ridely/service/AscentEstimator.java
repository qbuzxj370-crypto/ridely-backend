package kr.ridely.service;

import kr.ridely.infra.ors.OrsRouteResult;

/**
 * 누적 상승 고도 산출기.
 *
 * 이 인터페이스가 존재하는 이유는 ORS가 주는 값을 믿을 수 없기 때문이다. ORS는 양의 고도 변화를 필터 없이 전부 더하고, 좌표 간격이 SRTM 격자와 겹쳐 평지에서도 값이 크게 부풀려진다. 실측에서 표고차 31m짜리 한강 구간에 상승 137.9m가 나왔고 그중 68.6m가 자전거로 오를 수 없는 경사에서 발생했다 — docs/shared/SCHEMA_CHANGE_POI.md 6.5.
 *
 * 지금은 원값을 그대로 쓴다(RawAscentEstimator). 우리가 보정한 숫자를 사용자에게 보여주려면 근거가 더 필요하고, 그 값이 운동 강도 판정까지 좌우하면 잘못된 판정이 조용히 퍼진다.
 *
 * 보정을 켤 때는 크기 임계값이 아니라 경사 상한으로 걸러야 한다. 노이즈가 큰 값으로 오기 때문에 "N미터 미만 변화 무시"로는 안 걸린다. 실측에서 10m 임계로도 104m가 남았고 경사 6% 상한이라야 21.7m가 됐다. 재계산에 필요한 3D 좌표는 OrsRouteResult.geometryGeoJson에 들어 있다.
 */
public interface AscentEstimator {

    /**
     * 경로의 누적 상승 고도(m).
     *
     * @param route ORS 경로 결과. 원값과 3D 좌표를 모두 담고 있다
     */
    double estimate(OrsRouteResult route);
}
