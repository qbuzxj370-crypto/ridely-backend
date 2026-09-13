package kr.ridely.service;

import kr.ridely.dto.route.CandidateDTO;
import kr.ridely.dto.route.CourseDesignDTO;
import kr.ridely.dto.route.RouteCandidatesDTO;
import kr.ridely.infra.llm.CourseDesignClient;
import kr.ridely.infra.llm.FallbackCourseDesigner;
import kr.ridely.infra.llm.LlmCallException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 경유지를 확정한다. LLM이 안 되면 규칙으로 대체한다.
 *
 * <b>왜 서비스에서 떼어 냈나.</b> {@code RouteRecommendServiceImpl}의 생성자 인자가 열여섯이라 이 분기만 보려 해도 서비스 전체를 세워야 했다. 여기 의존은 둘이라 익명 클래스로 흉내 낼 수 있다. 캐시(C3)가 들어오면 서비스가 더 커지므로 지금 나눈다.
 *
 * <h3>세 갈래</h3>
 *
 * <pre>
 * ① 정상                          LLM 결과를 그대로 쓴다
 * ② 고른 것이 전부 비실재           한 번 더 부른다. 그래도 비면 ③
 * ③ 호출 실패 또는 ②의 재시도 실패   FallbackCourseDesigner
 * </pre>
 *
 * <b>②를 「전부 걸렀을 때」로 한정한 이유.</b> 네 곳 중 하나만 틀려도 재호출하면 LLM 호출이 자주 두 배가 된다. 비실재 ID가 얼마나 나오는지는 아직 재지 않았다 - 로그(「실재하지 않는 경유지를 걸렀다」)가 남고 있으니 게이트를 돌려 세면 조건을 좁힐 수 있다. 그 전까지는 현재 동작보다 나빠지지 않는 쪽을 고른다.
 *
 * <b>경유지가 하나도 없으면 대체한다.</b> 예전에는 그대로 진행했다. 그런데 출발지와 도착지만 남으면 순환 코스는 두 점이 같아 거리가 0이 되고 ORS에서 실패한다. 실패를 뒤로 미루는 것보다 여기서 채우는 편이 낫다.
 *
 * <b>StructuredLlmCaller의 재시도와 층이 다르다.</b> 저쪽은 JSON이 깨졌을 때고 여기는 JSON은 멀쩡한데 내용이 실재하지 않을 때다. 둘이 겹쳐 최악 네 번이 될 수 있지만, 파싱 실패와 비실재 ID가 같은 요청에서 연달아 나는 경우라 드물다.
 */
@Component
public class CourseDesignResolver {

    private static final Logger log = LoggerFactory.getLogger(CourseDesignResolver.class);

    private final CourseDesignClient courseDesignClient;
    private final FallbackCourseDesigner fallbackCourseDesigner;

    public CourseDesignResolver(CourseDesignClient courseDesignClient,
                                FallbackCourseDesigner fallbackCourseDesigner) {
        this.courseDesignClient = courseDesignClient;
        this.fallbackCourseDesigner = fallbackCourseDesigner;
    }

    /**
     * 설계 결과와 확정된 경유지, 그리고 대체 여부.
     *
     * 셋을 함께 돌려주는 이유는 호출부가 셋 다 필요해서다. 설계는 코멘트 생성의 입력이고, 경유지는 ORS 좌표가 되며, 대체 여부는 응답의 aiProvider가 된다.
     */
    public record Resolved(CourseDesignDTO design, List<CandidateDTO> waypoints, boolean fallback) {
    }

    public Resolved resolve(RouteCandidatesDTO candidates, double targetDistanceKm,
                            boolean circular, double straightLineKm,
                            BigDecimal convenience, BigDecimal exercise, BigDecimal scenery) {
        try {
            CourseDesignDTO design = courseDesignClient.design(
                    candidates, targetDistanceKm, circular, straightLineKm,
                    convenience, exercise, scenery);
            List<CandidateDTO> waypoints = resolveWaypoints(design, candidates);

            if (waypoints.isEmpty() && !design.waypointsOrEmpty().isEmpty()) {
                log.warn("고른 경유지가 전부 실재하지 않는다. 설계를 한 번 더 부른다");
                design = courseDesignClient.design(
                        candidates, targetDistanceKm, circular, straightLineKm,
                        convenience, exercise, scenery);
                waypoints = resolveWaypoints(design, candidates);
            }

            if (!waypoints.isEmpty()) {
                return new Resolved(design, waypoints, false);
            }
            log.warn("경유지를 얻지 못했다. 규칙으로 고른다");

        } catch (LlmCallException e) {
            log.warn("코스 설계 호출이 실패했다. 규칙으로 고른다: {}", e.getMessage());
        }

        CourseDesignDTO design = fallbackCourseDesigner.design(candidates);
        return new Resolved(design, resolveWaypoints(design, candidates), true);
    }

    /**
     * LLM이 고른 경유지를 후보 목록과 대조해 실재하는 것만 남긴다.
     *
     * 없는 번호를 만들어내는 경우가 있다. 걸러낸 결과가 비면 위에서 재호출과 대체를 판단한다.
     */
    private List<CandidateDTO> resolveWaypoints(CourseDesignDTO design, RouteCandidatesDTO candidates) {
        List<CandidateDTO> resolved = new ArrayList<>();
        for (CourseDesignDTO.SelectedWaypoint selected : design.waypointsOrEmpty()) {
            Optional<CandidateDTO> found = candidates.find(selected.getType(), selected.getId());
            if (found.isEmpty()) {
                log.warn("실재하지 않는 경유지를 걸렀다: type={} id={}",
                        selected.getType(), selected.getId());
                continue;
            }
            resolved.add(found.get());
        }
        return resolved;
    }
}
