package kr.ridely.infra.llm;

import kr.ridely.dto.route.CandidateDTO;
import kr.ridely.dto.route.CourseDesignDTO;
import kr.ridely.dto.route.RouteCandidatesDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 코스 설계 LLM 클라이언트 (파이프라인 1단계).
 *
 * 후보 목록을 주면 "어디를 어떤 순서로 들를지"를 받는다. 좌표는 받지 않는다 — 실제 경로는 OrsClient가 그린다.
 *
 * 이 클래스는 값만 만든다. 문장과 배치는 prompts/course-design-user.st에 있고, 파일 로딩과 치환은 ChatClient가 한다.
 */
@Component
public class CourseDesignClient {

    private static final Logger log = LoggerFactory.getLogger(CourseDesignClient.class);

    /**
     * 급수대는 이름을 쓸 수 없다.
     *
     * 원본의 이름 컬럼에 시설명이 아니라 노선명이 들어 있어 한강 구간 전체가 "한강종주길"로 같다 (DATA_SOURCES 6.1의 5번). 이름으로 주면 LLM이 후보를 구분하지 못하고 이유 문장도 무의미해진다. 종류 이름으로 바꿔 부르고 위치는 진행 거리로 알린다.
     */
    private static final String TYPE_WATER = "WATER";
    private static final String WATER_LABEL = "급수대";

    /**
     * 경유 시 늘어나는 거리 배수.
     *
     * 축에서 벗어난 지점을 들르려면 갔다가 돌아와야 하므로 이탈 거리의 두 배가 붙는다. 실제 도로를 따라가면 더 늘지만, LLM에게는 어느 후보가 더 많이 우회하는지의 상대 비교만 있으면 된다.
     */
    private static final int DETOUR_MULTIPLIER = 2;

    /**
     * 우회 예산을 미터로 환산하는 계수.
     *
     * 여유 거리는 km로 안내하는데 후보 목록의 `우회 +Nm`은 미터다. 예산과 후보 값의 단위가 다르면 LLM이 둘을 더하지 못하므로 같은 단위로도 함께 준다.
     */
    private static final double KM_TO_M = 1000.0;

    private final StructuredLlmCaller llmCaller;
    private final LlmProperties properties;
    private final Resource systemPrompt;
    private final Resource userPrompt;

    public CourseDesignClient(StructuredLlmCaller llmCaller, LlmProperties properties,
                              @Value("classpath:prompts/coach-ridely-system.st") Resource systemPrompt,
                              @Value("classpath:prompts/course-design-user.st") Resource userPrompt) {
        this.llmCaller = llmCaller;
        this.properties = properties;
        this.systemPrompt = systemPrompt;
        this.userPrompt = userPrompt;
    }

    /**
     * 경유지를 고르게 한다.
     *
     * @param candidates       수집된 후보. 여기 없는 것은 고를 수 없다
     * @param targetDistanceKm 목표 주행 거리
     * @param circular         도착지 없이 출발지로 되돌아오는 코스인지
     * @param straightLineKm   출발지~도착지 직선거리. 순환 코스면 0이다
     */
    public CourseDesignDTO design(RouteCandidatesDTO candidates, double targetDistanceKm,
                                  boolean circular, double straightLineKm,
                                  BigDecimal convenience, BigDecimal exercise, BigDecimal scenery) {

        double slackKm = Math.max(0, targetDistanceKm - straightLineKm);

        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("targetDistanceKm", trim(targetDistanceKm));
        variables.put("routeShape", circular ? "출발지로 되돌아오는 순환 코스" : "출발지에서 도착지까지 가는 편도 코스");
        variables.put("circular", circular);
        variables.put("straightLineKm", trim(round1(straightLineKm)));
        variables.put("slackKm", trim(round1(slackKm)));
        variables.put("detourBudgetM", String.valueOf(Math.round(slackKm * KM_TO_M)));
        variables.put("priorityConvenience", convenience.toPlainString());
        variables.put("priorityExercise", exercise.toPlainString());
        variables.put("priorityScenery", scenery.toPlainString());
        variables.put("tours", toRows(candidates.getTours(), targetDistanceKm));
        variables.put("waters", toRows(candidates.getWaters(), targetDistanceKm));
        variables.put("repairShops", toRows(candidates.getRepairShops(), targetDistanceKm));
        variables.put("bikeStations", toRows(candidates.getBikeStations(), targetDistanceKm));

        CourseDesignDTO design = llmCaller.call("코스 설계", systemPrompt, userPrompt,
                variables, properties.designTemperature(), CourseDesignDTO.class);

        log.info("코스 설계 결과: 경유지 {}곳 — {}",
                design.waypointsOrEmpty().size(), design.getDesignIntent());
        return design;
    }

    /**
     * 후보를 템플릿이 읽을 수 있는 형태로 바꾼다.
     *
     * 값을 전부 문자열로 미리 만든다. StringTemplate은 숫자를 그대로 찍어 12.0 같은 표기가 나오고, 소수점이 남으면 LLM이 따라 써서 문장이 어색해진다. 포맷은 Java가, 배치는 템플릿이 맡는다.
     */
    private List<Map<String, String>> toRows(List<CandidateDTO> items, double targetDistanceKm) {
        if (items == null) {
            return List.of();
        }
        return items.stream().map(c -> {
            Map<String, String> row = new LinkedHashMap<>();
            row.put("type", c.getType());
            row.put("id", String.valueOf(c.getId()));
            row.put("label", label(c));
            row.put("progressKm", progressKm(c, targetDistanceKm));
            row.put("offRouteM", String.valueOf(orZero(c.getDistanceM())));
            row.put("detourM", String.valueOf(orZero(c.getDistanceM()) * DETOUR_MULTIPLIER));
            return row;
        }).toList();
    }

    private String label(CandidateDTO candidate) {
        if (TYPE_WATER.equals(candidate.getType())) {
            return WATER_LABEL;
        }
        String name = candidate.getName();
        return name == null || name.isBlank() ? WATER_LABEL : name;
    }

    /** 순환 코스는 축이 없어 진행도가 없다. 그때는 물음표로 두어 위치를 단정하지 않게 한다 */
    private String progressKm(CandidateDTO candidate, double targetDistanceKm) {
        Double progressKm = candidate.progressKm(targetDistanceKm);
        return progressKm == null ? "?" : trim(progressKm);
    }

    private int orZero(Integer value) {
        return value == null ? 0 : value;
    }

    private double round1(double value) {
        return Math.round(value * 10) / 10.0;
    }

    /** 12.0을 "12"로 줄인다 */
    private String trim(double value) {
        if (value == Math.floor(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }
}
