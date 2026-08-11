package kr.ridely.infra.durunubi;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GPX 파서 단위 테스트 (스파이크 전용).
 *
 * <p>API 호출 전에 파서가 맞게 도는지 먼저 확인하려고 둔다.
 * 개발계정 일 1,000건 한도를 파서 디버깅에 쓰지 않기 위해서다.
 *
 * <p>기대값은 haversine(R=6371008.8m)으로 별도 계산해 넣었다.
 * 우리가 행안부 CSV를 분석할 때 쓴 식과 같아야 두 수치를 직접 대조할 수 있다.
 */
class GpxParserTest {

    private final GpxParser parser = new GpxParser();

    /**
     * 픽스처 설계 의도:
     * <ul>
     *   <li>trkseg 2개 — 구간 경계를 세는 핵심 기능</li>
     *   <li>wpt 1개 — trkpt와 섞여 세어지면 안 된다</li>
     *   <li>ele가 일부에만 — 고도 보유 여부를 세는 로직 확인</li>
     *   <li>기본 네임스페이스 선언 — 실제 GPX가 그렇다. local name으로 찾는지 확인</li>
     *   <li>lat/lon이 같은 위도라 거리 계산이 손으로 검산 가능</li>
     * </ul>
     */
    private static final String FIXTURE_GPX = """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" creator="test" xmlns="http://www.topografix.com/GPX/1/1">
              <wpt lat="37.5400" lon="126.9000"><name>인증센터</name></wpt>
              <trk>
                <name>테스트 코스</name>
                <trkseg>
                  <trkpt lat="37.5400" lon="126.9000"><ele>10</ele></trkpt>
                  <trkpt lat="37.5400" lon="126.9100"><ele>12</ele></trkpt>
                  <trkpt lat="37.5400" lon="126.9200"></trkpt>
                </trkseg>
                <trkseg>
                  <trkpt lat="37.5000" lon="127.0000"></trkpt>
                  <trkpt lat="37.5000" lon="127.0100"></trkpt>
                </trkseg>
              </trk>
            </gpx>
            """;

    @Test
    @DisplayName("trkseg 개수와 구간별 길이를 읽는다 — 스파이크 판정의 핵심")
    void parsesSegments() {
        GpxAnalysis result = parser.analyze(FIXTURE_GPX);

        assertThat(result.isParsed()).isTrue();
        assertThat(result.getTrkCount()).isEqualTo(1);
        assertThat(result.getTrksegCount()).isEqualTo(2);   // ★ 원본이 명시한 구간 수
        assertThat(result.getTrkptCount()).isEqualTo(5);    // wpt는 섞이지 않는다
        assertThat(result.getWptCount()).isEqualTo(1);
        assertThat(result.getElevationPointCount()).isEqualTo(2);

        // 구간 사이 점프(126.92,37.54 → 127.00,37.50 약 9km)는 길이에 더하지 않는다.
        // 이 동작이 MultiLineString을 쓰려는 이유 그 자체다.
        assertThat(result.getSegmentLengthsKm()).containsExactly(1.8, 0.9);
        assertThat(result.getTotalLengthKm()).isEqualTo(2.7);

        // 구간 내부 간격만 본다. 구간 경계의 9km는 최대 간격에 잡히면 안 된다
        assertThat(result.getMaxGapWithinSegmentM()).isEqualTo(882);
        assertThat(result.getMedianGapWithinSegmentM()).isEqualTo(882);
    }

    @Test
    @DisplayName("GPX가 아닌 응답(HTML 오류 페이지)을 거른다")
    void rejectsNonGpx() {
        GpxAnalysis result = parser.analyze("<!DOCTYPE html><html><body>오류가 발생했습니다</body></html>");

        assertThat(result.isParsed()).isFalse();
        assertThat(result.getFailReason()).contains("GPX가 아닌 응답");
    }

    @Test
    @DisplayName("빈 본문을 거른다")
    void rejectsEmptyBody() {
        assertThat(parser.analyze(null).isParsed()).isFalse();
        assertThat(parser.analyze("   ").isParsed()).isFalse();
    }

    @Test
    @DisplayName("trkseg가 없는 GPX도 예외 없이 분석 결과를 낸다")
    void handlesGpxWithoutTrack() {
        String wptOnly = """
                <?xml version="1.0" encoding="UTF-8"?>
                <gpx version="1.1" xmlns="http://www.topografix.com/GPX/1/1">
                  <wpt lat="37.54" lon="126.90"/>
                </gpx>
                """;

        GpxAnalysis result = parser.analyze(wptOnly);

        assertThat(result.isParsed()).isTrue();
        assertThat(result.getTrksegCount()).isZero();
        assertThat(result.getTotalLengthKm()).isZero();
    }
}
