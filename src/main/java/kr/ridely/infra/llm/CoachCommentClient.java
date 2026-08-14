package kr.ridely.infra.llm;

import kr.ridely.dto.route.CandidateDTO;
import kr.ridely.dto.route.CoachCommentDTO;
import kr.ridely.dto.route.CourseDesignDTO;
import kr.ridely.dto.route.RouteCandidatesDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 트레이너 코멘트 LLM 클라이언트 (파이프라인 마지막 단계).
 *
 * 확정된 경로와 경유지를 주면 Coach Ridely 톤의 자연어를 받는다. 설계 단계와 시스템 프롬프트는 같고 지시문과 온도만 다르다. 코멘트는 자연어라 온도를 조금 높인다.
 *
 * 톤 검증은 아직 없다. 여기서는 출력을 로그로 남기기만 한다 — 무엇을 걸러야 할지는 실제 출력을 모아 봐야 정할 수 있다.
 */
@Component
public class CoachCommentClient {

    private static final Logger log = LoggerFactory.getLogger(CoachCommentClient.class);

    private static final String SYSTEM_PROMPT_FILE = "coach-ridely-system.txt";
    private static final String USER_PROMPT_FILE = "coach-comment-user.txt";

    private static final String TYPE_WATER = "WATER";
    private static final String WATER_LABEL = "급수대";

    /** 경유지를 하나도 못 찾았을 때 프롬프트에 넣을 문구. 빈 값으로 두면 LLM이 지어낸다 */
    private static final String NO_WAYPOINT = "(경유지 없음)";

    private final PromptLoader promptLoader;
    private final StructuredLlmCaller llmCaller;
    private final LlmProperties properties;

    public CoachCommentClient(PromptLoader promptLoader, StructuredLlmCaller llmCaller,
                              LlmProperties properties) {
        this.promptLoader = promptLoader;
        this.llmCaller = llmCaller;
        this.properties = properties;
    }

    /**
     * 코스 해설을 생성한다.
     *
     * @param design          설계 결과. 경유지 이유와 설계 의도를 프롬프트에 그대로 넘긴다
     * @param candidates      경유지 이름을 되찾기 위한 원본 후보 목록
     * @param targetDistanceKm 요청한 목표 거리
     * @param totalDistanceKm  ORS가 계산한 실제 거리
     * @param durationMin      예상 소요 시간(분)
     * @param intensityLevel   산출된 운동 강도
     */
    public CoachCommentDTO generate(CourseDesignDTO design, RouteCandidatesDTO candidates,
                                    double targetDistanceKm, double totalDistanceKm,
                                    int durationMin, String intensityLevel) {

        Map<String, String> values = new LinkedHashMap<>();
        values.put("TOTAL_DISTANCE_KM", String.valueOf(totalDistanceKm));
        values.put("DURATION_MIN", String.valueOf(durationMin));
        values.put("INTENSITY_LEVEL", intensityLevel);
        values.put("TARGET_DISTANCE_KM", String.valueOf(targetDistanceKm));
        values.put("DESIGN_INTENT", orEmpty(design.getDesignIntent()));
        values.put("WAYPOINTS", renderWaypoints(design, candidates, targetDistanceKm));

        String userPrompt = promptLoader.render(USER_PROMPT_FILE, values);
        log.debug("코멘트 생성 프롬프트:\n{}", userPrompt);

        CoachCommentDTO comment = llmCaller.call("코멘트 생성",
                promptLoader.load(SYSTEM_PROMPT_FILE), userPrompt,
                properties.commentTemperature(), CoachCommentDTO.class);
        normalizeBlanks(comment);

        // 톤 검증기를 만들 재료다. 실제 출력을 모아야 무엇을 거를지 정할 수 있다
        log.info("코멘트 생성 결과: title={} / highlights={} / comment={}",
                comment.getTitle(), comment.highlightsOrEmpty().size(), comment.getCoachComment());
        return comment;
    }

    /**
     * 빈 문자열을 null로 바꾼다.
     *
     * 프롬프트에서 "비워 두세요"라고 하면 LLM이 null이 아니라 ""를 돌려준다(실측). 그대로 저장하면 ai_danger_zone_alert에 빈 문자열이 들어가고, 화면에서 "값이 있는데 내용이 없는" 상태가 된다. 없는 것은 null이어야 한다.
     */
    private void normalizeBlanks(CoachCommentDTO comment) {
        comment.setDangerZoneAlert(nullIfBlank(comment.getDangerZoneAlert()));
        comment.setNextStepSuggestion(nullIfBlank(comment.getNextStepSuggestion()));
    }

    private String nullIfBlank(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /**
     * 확정된 경유지를 프롬프트용 텍스트로 만든다.
     *
     * 설계가 돌려준 것은 (종류, 번호, 이유)뿐이라 이름과 위치는 후보 목록에서 되찾아야 한다. 이 시점에는 실재하지 않는 번호가 이미 걸러져 있지만, 방어적으로 못 찾은 건은 건너뛴다.
     */
    private String renderWaypoints(CourseDesignDTO design, RouteCandidatesDTO candidates,
                                   double targetDistanceKm) {
        StringBuilder sb = new StringBuilder();
        for (CourseDesignDTO.SelectedWaypoint selected : design.waypointsOrEmpty()) {
            Optional<CandidateDTO> found = candidates.find(selected.getType(), selected.getId());
            if (found.isEmpty()) {
                // 조용히 넘기면 경유지가 통째로 사라져도 응답만 보고는 알 수 없다.
                // 실제로 type에 한국어가 와서 전 건이 여기 걸린 적이 있다
                log.warn("경유지를 후보에서 찾지 못했다: type={} id={} — 프롬프트의 type 지시를 확인한다",
                        selected.getType(), selected.getId());
                continue;
            }
            CandidateDTO candidate = found.get();

            sb.append("- ");
            Double progressKm = candidate.progressKm(targetDistanceKm);
            if (progressKm != null) {
                sb.append(progressKm).append("km 지점 | ");
            }
            sb.append(displayName(candidate))
                    .append(" | ").append(orEmpty(selected.getReason()))
                    .append('\n');
        }
        return sb.isEmpty() ? NO_WAYPOINT : sb.toString();
    }

    /** 급수대는 원본 이름이 전부 노선명이라 구분에 쓸 수 없다 — DATA_SOURCES 6.1의 5번 */
    private String displayName(CandidateDTO candidate) {
        if (TYPE_WATER.equals(candidate.getType())) {
            return WATER_LABEL;
        }
        String name = candidate.getName();
        return name == null || name.isBlank() ? WATER_LABEL : name;
    }

    private String orEmpty(String value) {
        return value == null ? "" : value;
    }
}
