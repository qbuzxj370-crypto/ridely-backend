package kr.ridely.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 운동 강도 산출 단위 테스트.
 *
 * 경계값을 고정한다. 기획서의 강도 표를 옮긴 값이라 코드만 보고는 10km가 LIGHT인지 MODERATE인지 알 수 없고, 경계가 한 칸 밀려도 아무것도 깨지지 않은 채 사용자에게 다른 라벨이 나간다.
 *
 * 고도를 쓰지 않는다는 사실도 함께 고정한다. 표에는 고도 기준이 있지만 ORS 상승값이 평지에서 부풀려져 한강 코스가 HARD로 잘못 올라간다 — SCHEMA_CHANGE_POI.md 6.5. 나중에 고도를 다시 넣을 때 이 테스트가 "거리만으로 판정한다"는 현재 계약을 드러낸다.
 */
class IntensityCalculatorTest {

    private final IntensityCalculator calculator = new IntensityCalculator();

    @ParameterizedTest
    @DisplayName("거리 구간으로 강도를 매긴다 — ~10 LIGHT / 10~20 MODERATE / 20~35 HARD / 35~ CHALLENGE")
    @CsvSource({
            "0,     LIGHT",
            "9.999, LIGHT",
            "10,    MODERATE",
            "19.999, MODERATE",
            "20,    HARD",
            "34.999, HARD",
            "35,    CHALLENGE",
            "100,   CHALLENGE"
    })
    void classifiesByDistance(double totalDistanceKm, String expected) {
        assertThat(calculator.calculate(totalDistanceKm)).isEqualTo(expected);
    }

    @Test
    @DisplayName("경계값은 위 구간에 속한다 — 10km는 MODERATE다")
    void boundaryBelongsToUpperBand() {
        // 부등호 방향이 뒤집히는 실수를 잡는다. 10.0이 LIGHT로 떨어지면
        // 표의 "10~20 MODERATE"와 어긋난다
        assertThat(calculator.calculate(10.0)).isEqualTo("MODERATE");
        assertThat(calculator.calculate(20.0)).isEqualTo("HARD");
        assertThat(calculator.calculate(35.0)).isEqualTo("CHALLENGE");
    }

    @Test
    @DisplayName("실측 사례가 같은 라벨을 유지한다")
    void matchesMeasuredCases() {
        // 거리 보정 검증에서 실제로 나온 값들이다.
        // 보정 전 6.175km가 LIGHT, 보정 후 12.7km가 MODERATE로 바뀌었다
        assertThat(calculator.calculate(6.175)).isEqualTo("LIGHT");
        assertThat(calculator.calculate(12.700)).isEqualTo("MODERATE");
        assertThat(calculator.calculate(15.479)).isEqualTo("MODERATE");
        assertThat(calculator.calculate(31.973)).isEqualTo("HARD");
    }
}
