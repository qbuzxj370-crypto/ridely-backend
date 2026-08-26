package kr.ridely.service;

import org.springframework.stereotype.Component;

/**
 * 운동 강도 라벨 산출기.
 *
 * 기획서의 강도 표를 그대로 옮긴 것이다. 거리 구간별로 LIGHT·MODERATE·HARD·CHALLENGE를 매긴다.
 *
 * 표에는 누적 고도 기준도 함께 있지만(50 / 150 / 300m) 지금은 쓰지 않는다. ORS의 상승 고도가 평지에서 크게 부풀려져, 한강 코스가 HARD로 잘못 올라간다. 실측에서 12km 한강 구간에 상승 167m가 나왔는데 표대로면 MODERATE 상한(150m)을 이미 넘긴 값이다 - docs/shared/SCHEMA_CHANGE_POI.md 6.5.
 *
 * <b>고도를 되돌릴 방법을 여섯 구간 실측으로 찾아봤으나 전부 막혔다.</b> 경사 상한은 실제 오르막을 파괴하고(표고차 236m가 12.9m로), 리샘플링은 간격을 정할 근거가 없고, 국토지리정보원 5m DEM은 공개제한이라 받을 수 없다. 정답을 모르는 것이 근본 원인이다 - docs/shared/SPIKE_ELEVATION.md.
 *
 * 그래서 거리만으로 판정한다. 이건 임시 조치가 아니라 실측이 뒷받침한 결론이다. 짧지만 가파른 코스를 LIGHT로 부르는 한계가 있지만, 평지를 HARD로 부르는 것보다 덜 틀린다. MVP 서비스 지역이 한강 평지라 후자가 훨씬 자주 일어난다.
 */
@Component
public class IntensityCalculator {

    /**
     * 거리 구간 경계(km). 기획서의 강도 표 기준이다.
     *   LIGHT     ~10
     *   MODERATE  10~20
     *   HARD      20~35
     *   CHALLENGE 35~
     */
    private static final double MODERATE_FROM_KM = 10.0;
    private static final double HARD_FROM_KM = 20.0;
    private static final double CHALLENGE_FROM_KM = 35.0;

    private static final String LEVEL_LIGHT = "LIGHT";
    private static final String LEVEL_MODERATE = "MODERATE";
    private static final String LEVEL_HARD = "HARD";
    private static final String LEVEL_CHALLENGE = "CHALLENGE";

    /**
     * 실제 주행 거리로 강도를 매긴다.
     *
     * 목표 거리가 아니라 실측 거리를 쓴다. 12km를 요청했는데 경유지 배치 때문에 22km가 나왔다면 라이더가 실제로 타는 것은 22km다.
     *
     * @param totalDistanceKm ORS가 계산한 실제 주행 거리
     */
    public String calculate(double totalDistanceKm) {
        if (totalDistanceKm >= CHALLENGE_FROM_KM) {
            return LEVEL_CHALLENGE;
        }
        if (totalDistanceKm >= HARD_FROM_KM) {
            return LEVEL_HARD;
        }
        if (totalDistanceKm >= MODERATE_FROM_KM) {
            return LEVEL_MODERATE;
        }
        return LEVEL_LIGHT;
    }
}
