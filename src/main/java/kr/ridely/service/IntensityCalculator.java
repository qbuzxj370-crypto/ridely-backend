package kr.ridely.service;

import org.springframework.stereotype.Component;

/**
 * 운동 강도 라벨 산출기.
 *
 * 기획서의 강도 표를 그대로 옮긴 것이다. 거리 구간별로 LIGHT·MODERATE·HARD·CHALLENGE를 매긴다.
 *
 * 표에는 누적 고도 기준도 함께 있지만(50 / 150 / 300m) 지금은 쓰지 않는다. ORS의 상승 고도가 평지에서 크게 부풀려져, 한강 코스가 HARD로 잘못 올라간다. 실측에서 12km 한강 구간에 상승 137.9m가 나왔는데 표대로면 MODERATE 상한(150m)에 육박하는 값이다 — docs/shared/SCHEMA_CHANGE_POI.md 6.5.
 *
 * 고도를 판정에 다시 넣으려면 AscentEstimator를 경사 상한 방식으로 바꾸고, 여러 구간을 실측해 상한값(6%인지 8%인지)을 정한 뒤, 이 클래스에 고도 항을 추가한다. 그 전까지는 거리만으로 판정하는 편이 덜 틀린다.
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
