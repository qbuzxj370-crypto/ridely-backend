package kr.ridely.dto.route;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * LLM 코스 설계 결과 (Structured Output).
 *
 * 파이프라인 1단계다. 후보 목록을 주면 "어디를 어떤 순서로 들를지"를 돌려준다. 좌표는 만들지 않는다 — 실제 경로는 그다음 단계에서 ORS가 그린다.
 *
 * 스키마를 얕고 좁게 유지한 이유는 파싱 안정성 때문이다. 중첩이 깊거나 필드가 많으면 구조화 출력이 자주 깨진다. 여기 있는 것이 최소한이다.
 *
 * ⚠️ selectedWaypoints의 (type, id)가 실재한다는 보장이 없다. LLM이 없는 번호를 만들어내는 경우가 있어 RouteCandidatesDTO.find로 걸러야 한다. 지금은 걸러내기만 하고 재호출은 하지 않는다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CourseDesignDTO {

    /** 고른 경유지. 배열 순서가 주행 순서다 */
    private List<SelectedWaypoint> selectedWaypoints = new ArrayList<>();

    /** 이 코스를 이렇게 짠 이유. 다음 단계 코멘트 생성의 입력이 된다 */
    private String designIntent;

    public List<SelectedWaypoint> waypointsOrEmpty() {
        return selectedWaypoints == null ? List.of() : selectedWaypoints;
    }

    /**
     * 경유지 한 곳.
     *
     * 필드 이름을 후보(CandidateDTO)와 맞춰 뒀다. LLM에게 준 형태 그대로 돌려받아야 대조가 단순해진다.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SelectedWaypoint {

        /** TOUR / WATER / REPAIR_SHOP / BIKE_STATION */
        private String type;

        /** 후보 목록에 있던 번호 */
        private Long id;

        /** 여기를 고른 이유. 코스 해설과 하이라이트의 재료가 된다 */
        private String reason;
    }
}
