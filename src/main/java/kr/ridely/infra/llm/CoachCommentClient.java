package kr.ridely.infra.llm;

import kr.ridely.dto.route.CandidateDTO;
import kr.ridely.dto.route.CoachCommentDTO;
import kr.ridely.dto.route.CourseDesignDTO;
import kr.ridely.dto.route.PassingDangerZoneDTO;
import kr.ridely.dto.route.RouteCandidatesDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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

    private static final String TYPE_WATER = "WATER";
    private static final String WATER_LABEL = "급수대";

    /**
     * 위험 등급 표시명.
     *
     * 저장값은 영문인데 프롬프트에는 한국어로 준다. 톤 지시가 "주의는 살피면서, 경고는 페이스를 늦춰요"처럼 한국어라 등급도 같은 말로 두어야 대응이 분명해진다.
     */
    private static final Map<String, String> DANGER_LEVEL_LABELS = Map.of(
            "CAUTION", "주의",
            "WARNING", "경고",
            "DANGER", "위험");

    private final StructuredLlmCaller llmCaller;
    private final LlmProperties properties;
    private final Resource systemPrompt;
    private final Resource userPrompt;

    public CoachCommentClient(StructuredLlmCaller llmCaller, LlmProperties properties,
                              @Value("classpath:prompts/coach-ridely-system.st") Resource systemPrompt,
                              @Value("classpath:prompts/coach-comment-user.st") Resource userPrompt) {
        this.llmCaller = llmCaller;
        this.properties = properties;
        this.systemPrompt = systemPrompt;
        this.userPrompt = userPrompt;
    }

    /**
     * 코스 해설을 생성한다.
     *
     * @param design           설계 결과. 경유지 이유와 설계 의도를 프롬프트에 그대로 넘긴다
     * @param candidates       경유지 이름을 되찾기 위한 원본 후보 목록
     * @param targetDistanceKm 요청한 목표 거리
     * @param circular         도착지 없이 출발지로 되돌아오는 코스인지
     * @param totalDistanceKm  ORS가 계산한 실제 거리
     * @param durationMin      예상 소요 시간(분)
     * @param intensityLevel   산출된 운동 강도
     * @param dangerZones      코스가 지나는 사고다발지역. 비어 있으면 경고문을 만들지 않는다
     * @param avoidApplied     회피 정책을 적용해 그린 코스인지. 적용했어도 피하지 않는 등급은 dangerZones에 남는다
     */
    public CoachCommentDTO generate(CourseDesignDTO design, RouteCandidatesDTO candidates,
                                    double targetDistanceKm, boolean circular,
                                    double totalDistanceKm,
                                    int durationMin, String intensityLevel,
                                    List<PassingDangerZoneDTO> dangerZones, boolean avoidApplied) {

        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("dangerZones", toDangerRows(dangerZones));
        // 결과가 아니라 정책으로 넘긴다. 실제로 피한 구역이 있었는지는 알 수 없고
        // (비교 호출이 한 번 더 필요하다), 어느 지점을 피했는지는 라이더가 알 필요도 없다
        variables.put("avoidApplied", avoidApplied);
        variables.put("routeShape", circular ? "출발지로 되돌아오는 순환 코스" : "출발지에서 도착지까지 가는 편도 코스");
        variables.put("totalDistanceKm", String.valueOf(totalDistanceKm));
        variables.put("durationMin", String.valueOf(durationMin));
        variables.put("intensityLevel", intensityLevel);
        variables.put("targetDistanceKm", String.valueOf(targetDistanceKm));
        variables.put("designIntent", orEmpty(design.getDesignIntent()));
        variables.put("waypoints", toRows(design, candidates, totalDistanceKm));

        CoachCommentDTO comment = llmCaller.call("코멘트 생성", systemPrompt, userPrompt,
                variables, properties.commentTemperature(), CoachCommentDTO.class);
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
     * 확정된 경유지를 템플릿이 읽을 수 있는 형태로 바꾼다.
     *
     * 설계가 돌려준 것은 (종류, 번호, 이유)뿐이라 이름과 위치는 후보 목록에서 되찾아야 한다. 이 시점에는 실재하지 않는 번호가 이미 걸러져 있지만, 방어적으로 못 찾은 건은 건너뛴다.
     *
     * 위치는 목표 거리가 아니라 실측 거리로 환산한다. 응답의 waypoints와 같은 기준이어야 한다. 기준이 갈리면 같은 경유지가 코멘트에서는 11.7km 지점, 목록에서는 6.2km 지점으로 나온다.
     */
    private List<Map<String, String>> toRows(CourseDesignDTO design, RouteCandidatesDTO candidates,
                                             double totalDistanceKm) {
        List<Map<String, String>> rows = new ArrayList<>();
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

            Map<String, String> row = new LinkedHashMap<>();
            Double progressKm = candidate.progressKm(totalDistanceKm);
            row.put("progressKm", progressKm == null ? "?" : String.valueOf(progressKm));
            row.put("label", label(candidate));
            row.put("reason", orEmpty(selected.getReason()));
            rows.add(row);
        }
        return rows;
    }

    /**
     * 사고다발지를 템플릿이 읽을 수 있는 형태로 바꾼다.
     *
     * 사고 건수와 사망자 수를 한 문장으로 미리 합친다. 템플릿에서 조건 분기로 나누면 StringTemplate 하위 템플릿 안에 if가 들어가는데, 그 조합은 렌더링 검증과 부딪혀 이미 한 번 깨진 적이 있다. 포맷은 Java가, 배치는 템플릿이 맡는다.
     *
     * 등급은 한국어 표시명으로 바꾼다. 매핑에 없는 값이 오면 원본을 그대로 둔다 — 스키마 제약이 세 값만 허용하므로 실제로는 오지 않지만, 여기서 조용히 빈 값이 되는 것보다 낫다.
     */
    private List<Map<String, String>> toDangerRows(List<PassingDangerZoneDTO> zones) {
        if (zones == null) {
            return List.of();
        }
        return zones.stream().map(z -> {
            Map<String, String> row = new LinkedHashMap<>();
            row.put("progressKm", z.getDistanceFromStartKm() == null
                    ? "?" : z.getDistanceFromStartKm().toPlainString());
            row.put("level", DANGER_LEVEL_LABELS.getOrDefault(z.getDangerLevel(), z.getDangerLevel()));
            row.put("name", orEmpty(z.getSpotName()));
            row.put("detail", dangerDetail(z));
            return row;
        }).toList();
    }

    /** 사망자가 있으면 반드시 드러낸다. 등급이 DANGER로 올라가는 유일한 다른 조건이다 */
    private String dangerDetail(PassingDangerZoneDTO zone) {
        int deaths = zone.getDeathCount() == null ? 0 : zone.getDeathCount();
        int occurrences = zone.getOccurrenceCount() == null ? 0 : zone.getOccurrenceCount();
        return deaths > 0
                ? "최근 1년 사고 %d건, 사망 %d명".formatted(occurrences, deaths)
                : "최근 1년 사고 %d건".formatted(occurrences);
    }

    /** 급수대는 원본 이름이 전부 노선명이라 구분에 쓸 수 없다 — DATA_SOURCES 6.1의 5번 */
    private String label(CandidateDTO candidate) {
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
