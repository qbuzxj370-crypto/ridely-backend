package kr.ridely.infra.llm;

import kr.ridely.dto.route.CandidateDTO;
import kr.ridely.dto.route.CourseDesignDTO;
import kr.ridely.dto.route.RouteCandidatesDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LLM 없이 경유지를 고르는 규칙 단위 테스트.
 *
 * <b>여기서 고정하려는 것은 「목록이 아니라 코스가 나온다」다.</b> 거리순으로만 집으면 따릉이 대여소가 촘촘한 구간에서 네 곳이 전부 대여소가 되고, 축 순서를 안 맞추면 코스가 왔다 갔다 한다. 둘 다 규칙이 없으면 그럴듯하게 통과하고 결과만 나빠지는 종류다.
 */
class FallbackCourseDesignerTest {

    private static final String TYPE_TOUR = "TOUR";
    private static final String TYPE_BIKE_STATION = "BIKE_STATION";
    private static final String TYPE_WATER = "WATER";

    private final FallbackCourseDesigner designer = new FallbackCourseDesigner();

    @Test
    @DisplayName("한 타입이 아무리 많아도 타입별 상한을 지킨다")
    void doesNotLetOneTypeTakeOver() {
        // 따릉이는 한강변에 촘촘하다. 거리순으로만 집으면 전부 대여소가 된다
        RouteCandidatesDTO candidates = new RouteCandidatesDTO();
        candidates.setBikeStations(List.of(
                candidate(TYPE_BIKE_STATION, 1L, 10, 0.1),
                candidate(TYPE_BIKE_STATION, 2L, 20, 0.2),
                candidate(TYPE_BIKE_STATION, 3L, 30, 0.3),
                candidate(TYPE_BIKE_STATION, 4L, 40, 0.4)));
        candidates.setTours(List.of(candidate(TYPE_TOUR, 10L, 500, 0.5)));

        List<CourseDesignDTO.SelectedWaypoint> picked =
                designer.design(candidates).waypointsOrEmpty();

        assertThat(picked).extracting(CourseDesignDTO.SelectedWaypoint::getType)
                .filteredOn(TYPE_BIKE_STATION::equals)
                .hasSize(1);
        // 관광지가 축에서 훨씬 멀어도 들어간다. 이 서비스는 관광 코스다
        assertThat(picked).extracting(CourseDesignDTO.SelectedWaypoint::getType)
                .contains(TYPE_TOUR);
    }

    @Test
    @DisplayName("관광지는 둘까지 집는다")
    void picksTwoTours() {
        RouteCandidatesDTO candidates = new RouteCandidatesDTO();
        candidates.setTours(List.of(
                candidate(TYPE_TOUR, 1L, 100, 0.2),
                candidate(TYPE_TOUR, 2L, 200, 0.4),
                candidate(TYPE_TOUR, 3L, 300, 0.6)));

        assertThat(designer.design(candidates).waypointsOrEmpty()).hasSize(2);
    }

    @Test
    @DisplayName("축을 따라 순서대로 세운다")
    void sortsAlongAxis() {
        // 이 순서가 곧 주행 순서다. 어긋나면 코스가 앞뒤로 오간다
        RouteCandidatesDTO candidates = new RouteCandidatesDTO();
        candidates.setTours(List.of(candidate(TYPE_TOUR, 1L, 100, 0.8)));
        candidates.setWaters(List.of(candidate(TYPE_WATER, 2L, 100, 0.2)));
        candidates.setBikeStations(List.of(candidate(TYPE_BIKE_STATION, 3L, 100, 0.5)));

        assertThat(designer.design(candidates).waypointsOrEmpty())
                .extracting(CourseDesignDTO.SelectedWaypoint::getId)
                .containsExactly(2L, 3L, 1L);
    }

    @Test
    @DisplayName("고른 이유를 지어내지 않는다")
    void leavesReasonEmpty() {
        // 규칙으로 만든 "한강 전망이 좋아 골랐다"가 코멘트 재료로 들어가면
        // 없는 근거가 사용자에게 나간다
        RouteCandidatesDTO candidates = new RouteCandidatesDTO();
        candidates.setTours(List.of(candidate(TYPE_TOUR, 1L, 100, 0.3)));

        assertThat(designer.design(candidates).waypointsOrEmpty())
                .extracting(CourseDesignDTO.SelectedWaypoint::getReason)
                .containsOnlyNulls();
    }

    @Test
    @DisplayName("진행도가 없는 순환 코스에서도 동작한다")
    void handlesCircularWithoutProgress() {
        // 도착지가 없으면 축이 점 하나라 progressRatio가 null이다
        RouteCandidatesDTO candidates = new RouteCandidatesDTO();
        candidates.setTours(List.of(candidate(TYPE_TOUR, 1L, 100, null)));
        candidates.setWaters(List.of(candidate(TYPE_WATER, 2L, 200, null)));

        assertThat(designer.design(candidates).waypointsOrEmpty()).hasSize(2);
    }

    private CandidateDTO candidate(String type, long id, int distanceM, Double progressRatio) {
        CandidateDTO candidate = new CandidateDTO(type, id, type + "-" + id,
                37.54, 126.90, distanceM);
        candidate.setProgressRatio(progressRatio);
        return candidate;
    }
}
