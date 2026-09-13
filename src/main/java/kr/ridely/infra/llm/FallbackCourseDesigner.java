package kr.ridely.infra.llm;

import kr.ridely.dto.route.CandidateDTO;
import kr.ridely.dto.route.CourseDesignDTO;
import kr.ridely.dto.route.RouteCandidatesDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * LLM 없이 경유지를 고른다. 코스 설계가 실패했을 때 쓴다.
 *
 * <b>코스를 못 주는 것보다 덜 좋은 코스를 주는 편이 낫다.</b> 회피 라우팅이 실패했을 때 회피를 포기하고 코스를 주기로 한 것과 같은 판단이다 (ADR-011, SPRINT_W4 C4-3).
 *
 * <b>이 클래스는 실패하지 않는다.</b> DB 후보만 쓰고 외부 호출이 없다. 후보가 0건인 경우는 RouteRecommendServiceImpl이 앞에서 ROUTE-006으로 걸러낸다. 그래서 「LLM 최종 실패」라는 상태가 없고, 설계에 있던 ROUTE-004를 넣지 않았다.
 *
 * <h3>선정 규칙</h3>
 *
 * 타입별로 하나씩 집고 축을 따라 순서대로 세운다. LLM이 하던 「왜 이곳인가」는 흉내 내지 않는다 - 흉내 낸 이유를 코멘트가 받아 쓰면 지어낸 근거가 사용자에게 나간다.
 *
 * 타입을 섞는 이유는 쏠림을 막기 위해서다. 거리순으로만 집으면 따릉이 대여소가 촘촘한 구간에서 네 곳이 전부 대여소가 된다. 그건 코스가 아니라 목록이다.
 *
 * 축에서 가까운 순으로 집는다. 멀리 있는 후보는 코스를 크게 돌리는데, 그 판단은 목표 거리를 봐야 할 수 있는 것이고 여기서는 하지 않는다. 거리 보정은 뒤에서 연장점이 따로 맞춘다.
 *
 * @see CourseDesignClient
 */
@Component
public class FallbackCourseDesigner {

    private static final Logger log = LoggerFactory.getLogger(FallbackCourseDesigner.class);

    /**
     * 타입별로 집을 개수.
     *
     * 관광지를 둘 집는 것은 이 서비스가 관광 코스이기 때문이다. 나머지는 편의시설이라 하나씩이면 족하다.
     */
    private static final int TOUR_PICKS = 2;
    private static final int FACILITY_PICKS = 1;

    /** 진행도가 없는 순환 코스는 정렬 기준이 없다. 뒤로 보낸다 */
    private static final double NO_PROGRESS = Double.MAX_VALUE;

    /**
     * 후보에서 경유지를 골라 설계 결과를 만든다.
     *
     * @param candidates 수집기가 모은 후보. 비어 있지 않아야 한다 (호출 전 ROUTE-006으로 걸러진다)
     * @return LLM 결과와 같은 타입. 호출부는 이것이 fallback인지 알 필요가 없다
     */
    public CourseDesignDTO design(RouteCandidatesDTO candidates) {
        List<CandidateDTO> picked = new ArrayList<>();
        picked.addAll(nearest(candidates.getTours(), TOUR_PICKS));
        picked.addAll(nearest(candidates.getWaters(), FACILITY_PICKS));
        picked.addAll(nearest(candidates.getRepairShops(), FACILITY_PICKS));
        picked.addAll(nearest(candidates.getBikeStations(), FACILITY_PICKS));

        // 축을 따라 세운다. 이 순서가 곧 주행 순서이므로 여기서 어긋나면 코스가 왔다 갔다 한다
        picked.sort(Comparator.comparingDouble(this::progressOf));

        CourseDesignDTO design = new CourseDesignDTO();
        design.setSelectedWaypoints(picked.stream().map(this::toWaypoint).toList());
        design.setDesignIntent(null);

        log.warn("코스 설계를 대체했다. 경유지 {}곳 (후보 {}건)", picked.size(), candidates.totalCount());
        return design;
    }

    /** 축에서 가까운 순으로 n개. 후보가 모자라면 있는 만큼만 */
    private List<CandidateDTO> nearest(List<CandidateDTO> pool, int n) {
        if (pool == null || pool.isEmpty()) {
            return List.of();
        }
        return pool.stream()
                .sorted(Comparator.comparingInt(c -> c.getDistanceM() == null
                        ? Integer.MAX_VALUE : c.getDistanceM()))
                .limit(n)
                .toList();
    }

    private double progressOf(CandidateDTO candidate) {
        return candidate.getProgressRatio() == null ? NO_PROGRESS : candidate.getProgressRatio();
    }

    /**
     * reason을 비운다.
     *
     * LLM이 쓰던 자리라 문장을 채워 넣고 싶어지지만, 「한강 전망이 좋아 골랐다」 같은 문장을 규칙으로 지어내면 그건 근거가 아니라 장식이다. 코멘트 생성기가 이 값을 재료로 쓰므로 지어낸 문장이 그대로 사용자에게 나간다. 비워 두면 코멘트 쪽이 없는 대로 처리한다.
     */
    private CourseDesignDTO.SelectedWaypoint toWaypoint(CandidateDTO candidate) {
        return new CourseDesignDTO.SelectedWaypoint(
                candidate.getType(), candidate.getId(), null);
    }
}
