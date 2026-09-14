package kr.ridely.service;

import kr.ridely.dto.route.CandidateDTO;
import kr.ridely.dto.route.CourseDesignDTO;
import kr.ridely.dto.route.RouteCandidatesDTO;
import kr.ridely.infra.llm.FallbackCourseDesigner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 경유지 선정 이유 조회 단위 테스트.
 *
 * <b>2026-09-13에 실제로 터진 자리다.</b> 규칙으로 고른 경유지는 reason이 없는데, 조회가 {@code map}을 {@code findFirst} 앞에 두고 있어 {@code Optional.of(null)}에서 NPE가 났다. 그 시점에는 코스도 코멘트도 이미 다 만들어진 뒤라, 응답을 조립하는 마지막 단계에서 500이 나갔다.
 *
 * <b>단위 테스트 194건이 전부 통과하는 채로 났다.</b> 리졸버는 격리해서 봤고 대체 산출물도 따로 봤는데, 그 둘을 이어 붙이는 자리를 아무도 안 봤다. 게이트에서 LLM을 죽여 보고서야 드러났다.
 *
 * 그래서 여기서 고정하는 것은 <b>대체가 만든 값을 조회가 견딘다</b>는 연결이다. 한쪽만 봐서는 잡히지 않는다.
 */
class WaypointReasonTest {

    private static final String TYPE_TOUR = "TOUR";
    private static final long TOUR_ID = 1L;

    @Test
    @DisplayName("LLM이 쓴 이유를 그대로 돌려준다")
    void returnsReasonFromDesign() {
        CourseDesignDTO design = new CourseDesignDTO();
        design.setSelectedWaypoints(List.of(
                new CourseDesignDTO.SelectedWaypoint(TYPE_TOUR, TOUR_ID, "한강이 내려다보인다")));

        assertThat(RouteRecommendServiceImpl.reasonOf(design, tour()))
                .isEqualTo("한강이 내려다보인다");
    }

    @Test
    @DisplayName("이유가 없는 경유지에서 터지지 않는다")
    void toleratesNullReason() {
        // findFirst 앞에 map을 두면 Optional.of(null)이 되어 NPE가 난다.
        // 규칙으로 고른 경유지는 항상 이 상태다
        CourseDesignDTO design = new CourseDesignDTO();
        design.setSelectedWaypoints(List.of(
                new CourseDesignDTO.SelectedWaypoint(TYPE_TOUR, TOUR_ID, null)));

        assertThat(RouteRecommendServiceImpl.reasonOf(design, tour())).isNull();
    }

    @Test
    @DisplayName("설계에 없는 경유지면 null이다")
    void returnsNullWhenNotSelected() {
        assertThat(RouteRecommendServiceImpl.reasonOf(new CourseDesignDTO(), tour())).isNull();
    }

    @Test
    @DisplayName("규칙으로 고른 설계를 그대로 넣어도 견딘다")
    void survivesFallbackDesign() {
        // 위 테스트들은 null reason을 손으로 만든 것이라, FallbackCourseDesigner가
        // 정말 그 값을 내는지는 보지 않는다. 둘을 이어 붙여야 실제 조합이 검증된다.
        RouteCandidatesDTO candidates = new RouteCandidatesDTO();
        candidates.setTours(List.of(tour()));

        CourseDesignDTO design = new FallbackCourseDesigner().design(candidates);

        assertThatCode(() -> RouteRecommendServiceImpl.reasonOf(design, tour()))
                .doesNotThrowAnyException();
        assertThat(RouteRecommendServiceImpl.reasonOf(design, tour())).isNull();
    }

    private CandidateDTO tour() {
        CandidateDTO candidate = new CandidateDTO(TYPE_TOUR, TOUR_ID, "선유도공원",
                37.5432, 126.8997, 120);
        candidate.setProgressRatio(0.4);
        return candidate;
    }
}
