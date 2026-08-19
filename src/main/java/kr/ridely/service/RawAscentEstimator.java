package kr.ridely.service;

import kr.ridely.infra.ors.OrsRouteResult;
import org.springframework.stereotype.Component;

/**
 * ORS가 준 상승 고도를 그대로 쓰는 산출기.
 *
 * 과대 계상된 값인 것을 알면서도 원값을 쓰는 이유는 두 가지다. 우리가 보정한 숫자를 사용자에게 보여주려면 여러 구간의 실측 비교가 필요하고, 보정 계수를 잘못 잡으면 원값보다 더 틀린 값이 나온다.
 *
 * 대신 이 값은 표시 전용이다. 운동 강도 판정에는 넣지 않는다 — IntensityCalculator 주석 참조.
 */
@Component
public class RawAscentEstimator implements AscentEstimator {

    @Override
    public double estimate(OrsRouteResult route) {
        return route.getAscentM();
    }
}
