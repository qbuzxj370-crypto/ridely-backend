package kr.ridely.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 좌표 간 거리 계산 단위 테스트.
 *
 * 값을 고정해두는 이유는 지구 반지름 때문이다. 노선 적재의 파트 분리 임계값(3km)이 R=6371008.8m로 낸 분석 결과이고 그 수치가 SCHEMA_CHANGE_ROUTE_GEOM.md 4장에 남아 있다. 반지름이 바뀌면 코드는 멀쩡히 돌면서 문서와 대조가 안 되는 상태가 된다.
 *
 * 위도 1도가 곧 반지름의 함수(R × π/180)라 첫 두 검사가 사실상 반지름을 고정한다.
 */
class GeoDistanceTest {

    /** 계산은 미터 단위이고 소수점 이하는 의미가 없다 */
    private static final double TOLERANCE_M = 1.0;

    @Test
    @DisplayName("위도 1도는 어디서나 약 111,195m — 반지름을 고정하는 검사다")
    void oneDegreeOfLatitude() {
        assertThat(GeoDistance.haversineM(0, 0, 0, 1))
                .isCloseTo(111195.08, within(TOLERANCE_M));
    }

    @Test
    @DisplayName("적도에서 경도 1도는 위도 1도와 같다")
    void oneDegreeOfLongitudeAtEquator() {
        assertThat(GeoDistance.haversineM(0, 0, 1, 0))
                .isCloseTo(111195.08, within(TOLERANCE_M));
    }

    @Test
    @DisplayName("경도 간격은 위도가 높아질수록 좁아진다 — 서울에서 경도 1도는 약 88,217m")
    void longitudeNarrowsWithLatitude() {
        // 후보 수집의 축 투영이 cos(위도)로 보정하는 근거다.
        // 보정 없이 위경도를 그대로 벡터로 다루면 서울에서 21% 어긋난다
        assertThat(GeoDistance.haversineM(126, 37.5, 127, 37.5))
                .isCloseTo(88216.57, within(TOLERANCE_M));
    }

    @Test
    @DisplayName("같은 점은 0이다")
    void samePointIsZero() {
        // 순환 코스는 출발지와 도착지가 같다. 여기서 0이 아니면
        // 여유 거리 계산이 조용히 틀어진다
        assertThat(GeoDistance.haversineM(126.9, 37.5, 126.9, 37.5)).isZero();
    }

    @Test
    @DisplayName("선유도~여의도 직선거리는 약 3,783m")
    void seonyudoToYeouido() {
        // 이 PR의 거리 보정을 실측한 구간이다. 목표 12km와의 차이가
        // 곧 연장점 계산의 입력이 되므로 값이 흔들리면 실측 기록과 대조가 안 된다
        assertThat(GeoDistance.haversineM(126.8975, 37.5445, 126.9339, 37.5265))
                .isCloseTo(3782.51, within(TOLERANCE_M));
    }

    @Test
    @DisplayName("방향이 바뀌어도 거리는 같다")
    void isSymmetric() {
        double forward = GeoDistance.haversineM(126.8975, 37.5445, 126.9339, 37.5265);
        double backward = GeoDistance.haversineM(126.9339, 37.5265, 126.8975, 37.5445);
        assertThat(forward).isEqualTo(backward);
    }
}
