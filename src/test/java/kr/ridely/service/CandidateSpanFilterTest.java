package kr.ridely.service;

import kr.ridely.dto.route.CandidateDTO;
import kr.ridely.dto.route.RouteCandidatesDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 후보 축 투영·축 밖 제외 단위 테스트.
 *
 * DB 없이 순수 계산만 본다. private 메서드를 리플렉션으로 부르는 이유는 AccidentZoneDangerLevelTest와 같다 — 수집기 내부 규칙이고 이것만 쓰려고 public API를 늘리고 싶지 않다.
 *
 * 여기서 고정하려는 것은 <b>진행도를 0~1로 자르지 않는다</b>는 계약이다. 범위 밖 값이 축을 벗어난 후보를 걸러내는 유일한 근거라, 자르는 순간 근거가 사라진다. 실제로 예전 구현이 Math.clamp를 쓰고 있었고 그 탓에 서로 다른 관광지 스물다섯 곳이 프롬프트에 모두 "12km 지점"으로 찍혀 나갔다. 후보 86건 중 36건이 그 상태였다.
 *
 * 범위 밖 값을 버그로 보고 다시 자르는 수정이 들어오면 이 테스트가 먼저 깨진다.
 */
class CandidateSpanFilterTest {

    /** 이 PR의 거리 보정을 실측한 구간이다 */
    private static final double START_LNG = 126.8975;
    private static final double START_LAT = 37.5445;
    private static final double END_LNG = 126.9339;
    private static final double END_LAT = 37.5265;

    /** 투영은 부동소수 연산이라 정확히 같지는 않다 */
    private static final double TOLERANCE = 1e-9;

    private final InfraCandidateCollector collector =
            new InfraCandidateCollector(null, null, null);

    // --- 축 투영 ---

    @Test
    @DisplayName("출발지는 0, 도착지는 1, 중간은 0.5다")
    void projectsOntoAxis() {
        RouteCandidatesDTO candidates = tours(
                at(1, START_LNG, START_LAT),
                at(2, midLng(), midLat()),
                at(3, END_LNG, END_LAT));

        fillProgress(candidates);

        assertThat(ratioOf(candidates, 1)).isCloseTo(0.0, within(TOLERANCE));
        assertThat(ratioOf(candidates, 2)).isCloseTo(0.5, within(TOLERANCE));
        assertThat(ratioOf(candidates, 3)).isCloseTo(1.0, within(TOLERANCE));
    }

    @Test
    @DisplayName("도착지 너머는 1을 넘고 출발지 이전은 음수다 — 자르지 않는다")
    void doesNotClampOutOfSpan() {
        // 이 테스트가 이 클래스의 존재 이유다.
        // 여의도로 가는 코스에 국립중앙박물관(도착지 동쪽 4km)이 후보로 들어오는 상황이고,
        // 값을 자르면 도착지에 딱 붙은 후보와 구분되지 않는다
        RouteCandidatesDTO candidates = tours(
                at(1, beyondEndLng(), beyondEndLat()),
                at(2, beforeStartLng(), beforeStartLat()));

        fillProgress(candidates);

        assertThat(ratioOf(candidates, 1)).isCloseTo(2.0, within(TOLERANCE));
        assertThat(ratioOf(candidates, 2)).isCloseTo(-1.0, within(TOLERANCE));
    }

    @Test
    @DisplayName("축에서 옆으로 벗어난 후보도 진행도는 0~1 안이다")
    void lateralCandidateStaysOnSpan() {
        // 진행도는 "경로의 어느 지점인가"이고 distanceM이 "얼마나 벗어났나"다.
        // 축 중간에서 북쪽으로 떨어진 지점은 옆으로 멀어도 진행도는 0.5 근처여야 한다
        RouteCandidatesDTO candidates = tours(at(1, midLng(), midLat() + 0.03));

        fillProgress(candidates);

        assertThat(ratioOf(candidates, 1)).isBetween(0.0, 1.0);
    }

    // --- 축 밖 제외 ---

    @Test
    @DisplayName("범위를 벗어난 후보를 버린다")
    void dropsOutOfSpanCandidates() {
        List<CandidateDTO> selected = selectOnSpan(List.of(
                withRatio(1, -0.01),
                withRatio(2, 0.0),
                withRatio(3, 0.5),
                withRatio(4, 1.0),
                withRatio(5, 1.01)), 10);

        assertThat(selected).extracting(CandidateDTO::getId)
                .containsExactly(2L, 3L, 4L);
    }

    @Test
    @DisplayName("순환 코스는 진행도가 없어 전부 남긴다")
    void keepsAllWhenCircular() {
        // 도착지가 없으면 축을 만들 수 없어 진행도가 null이다.
        // 거를 기준이 없는 것이지 후보가 부적합한 것이 아니다
        List<CandidateDTO> selected = selectOnSpan(List.of(
                withRatio(1, null),
                withRatio(2, null)), 10);

        assertThat(selected).hasSize(2);
    }

    @Test
    @DisplayName("입력 순서를 지킨 채 목표 개수까지만 자른다")
    void keepsPickOrderAndLimit() {
        // 조회가 근거리·원거리를 번갈아 올려보내므로 순서를 지켜야
        // 근거리와 원거리 비율이 유지된다
        List<CandidateDTO> selected = selectOnSpan(List.of(
                withRatio(1, 0.1),
                withRatio(2, 0.2),
                withRatio(3, 0.3),
                withRatio(4, 0.4)), 2);

        assertThat(selected).extracting(CandidateDTO::getId).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("30m 안에 겹친 후보는 하나만 남긴다")
    void removesNearDuplicates() {
        // 원본에 좌표가 몇 미터씩 어긋난 중복 등록이 있다.
        // 실측에서 급수대 8건 중 3건이 2.2~5.5m 거리의 쌍이었다
        CandidateDTO first = withRatio(1, 0.5);
        CandidateDTO duplicate = withRatio(2, 0.5);   // 약 11m
        CandidateDTO distinct = withRatio(3, 0.5);    // 약 111m

        setPosition(first, START_LNG, START_LAT);
        setPosition(duplicate, START_LNG, START_LAT + 0.0001);
        setPosition(distinct, START_LNG, START_LAT + 0.001);

        List<CandidateDTO> selected = selectOnSpan(List.of(first, duplicate, distinct), 10);

        assertThat(selected).extracting(CandidateDTO::getId).containsExactly(1L, 3L);
    }

    // --- 도우미 ---

    private void fillProgress(RouteCandidatesDTO candidates) {
        ReflectionTestUtils.invokeMethod(collector, "fillProgressRatio",
                candidates, START_LNG, START_LAT, END_LNG, END_LAT);
    }

    @SuppressWarnings("unchecked")
    private List<CandidateDTO> selectOnSpan(List<CandidateDTO> fetched, int limit) {
        return (List<CandidateDTO>) ReflectionTestUtils.invokeMethod(
                collector, "selectOnSpan", fetched, limit);
    }

    private RouteCandidatesDTO tours(CandidateDTO... items) {
        RouteCandidatesDTO candidates = new RouteCandidatesDTO();
        candidates.setTours(List.of(items));
        return candidates;
    }

    private CandidateDTO at(long id, double lng, double lat) {
        return new CandidateDTO("TOUR", id, "후보" + id, lat, lng, 0);
    }

    /**
     * 진행도만 지정한 후보. 위치는 번호마다 약 111m씩 벌린다.
     *
     * 같은 좌표에 놓으면 근접 중복 제거(30m)가 먼저 걸려 진행도 판정을 볼 수 없다. 중복 제거 자체를 보는 검사는 setPosition으로 좌표를 다시 잡는다.
     */
    private CandidateDTO withRatio(long id, Double ratio) {
        CandidateDTO candidate = at(id, START_LNG, START_LAT + id * 0.001);
        candidate.setProgressRatio(ratio);
        return candidate;
    }

    private void setPosition(CandidateDTO candidate, double lng, double lat) {
        candidate.setLng(lng);
        candidate.setLat(lat);
    }

    private Double ratioOf(RouteCandidatesDTO candidates, long id) {
        return candidates.all().stream()
                .filter(c -> c.getId() == id)
                .findFirst()
                .orElseThrow()
                .getProgressRatio();
    }

    private double midLng() {
        return (START_LNG + END_LNG) / 2;
    }

    private double midLat() {
        return (START_LAT + END_LAT) / 2;
    }

    private double beyondEndLng() {
        return END_LNG + (END_LNG - START_LNG);
    }

    private double beyondEndLat() {
        return END_LAT + (END_LAT - START_LAT);
    }

    private double beforeStartLng() {
        return START_LNG - (END_LNG - START_LNG);
    }

    private double beforeStartLat() {
        return START_LAT - (END_LAT - START_LAT);
    }
}
