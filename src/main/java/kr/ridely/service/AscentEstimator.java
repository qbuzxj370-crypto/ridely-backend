package kr.ridely.service;

import kr.ridely.infra.ors.OrsRouteResult;

/**
 * 누적 상승 고도 산출기.
 *
 * 이 인터페이스가 존재하는 이유는 ORS가 주는 값을 믿을 수 없기 때문이다. ORS는 양의 고도 변화를 필터 없이 전부 더하고, 좌표 간격이 SRTM 격자와 겹쳐 평지에서도 값이 크게 부풀려진다. 실측에서 표고차 31m짜리 한강 구간에 상승 119.5m가 나왔다 - docs/shared/SCHEMA_CHANGE_POI.md 6.5.
 *
 * <b>구현체는 RawAscentEstimator 하나뿐이고 원값을 그대로 쓴다.</b> 보정 방법을 세 가지 찾아봤는데 전부 막혔다.
 *
 *   - 경사 상한: 실제 오르막을 파괴한다. 표고차 236m인 남산이 6% 상한에서 12.9m로 나온다
 *   - 리샘플링: 오르막은 살리지만 간격을 정할 근거가 없다. 구간마다 최적값이 다르다
 *   - 국토지리정보원 DEM: 5m급은 공개제한이라 사업자만 신청할 수 있고, 공개된 90m급은 SRTM보다 나쁘다
 *
 * 근본 원인은 정답을 모른다는 것이다. 표고차는 하한일 뿐이라 어떤 보정이 맞는지 판정할 기준이 없다. 여섯 구간 실측과 근거는 docs/shared/SPIKE_ELEVATION.md에 있다.
 *
 * <b>새 구현체를 만들기 전에 그 문서를 먼저 읽을 것.</b> 다시 볼 조건은 10장에 있다 - 5m급 DEM 접근권을 얻거나, ORS를 셀프호스팅하며 고품질 고도를 넣거나, 실제 주행 GPS 고도 기록이 쌓여 정답을 알게 될 때다.
 */
public interface AscentEstimator {

    /**
     * 경로의 누적 상승 고도(m).
     *
     * @param route ORS 경로 결과. 원값과 3D 좌표를 모두 담고 있다
     */
    double estimate(OrsRouteResult route);
}
