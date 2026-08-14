package kr.ridely.dto.route;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * LLM 트레이너 코멘트 결과 (Structured Output).
 *
 * 파이프라인 마지막 단계다. 확정된 경로와 경유지를 주면 Coach Ridely 톤의 자연어를 돌려준다. 그대로 recommended_route의 ai_ 컬럼에 들어가고 앱에서는 코치 말풍선으로 보인다.
 *
 * 톤 검증기는 아직 없다. 지금은 출력을 로그로 모으기만 한다 — 무엇을 걸러야 할지는 실제 출력을 봐야 정할 수 있다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CoachCommentDTO {

    /** 코스 제목. 예: "한강 따라 14km, 적당히 땀 빼는 코스" */
    private String title;

    /** 코스의 핵심 포인트. 예: ["9km 지점 반포 급수대 경유", "여의도공원 풍경 구간"] */
    private List<String> highlights = new ArrayList<>();

    /** 코치의 한마디. 1~2문장 */
    private String coachComment;

    /**
     * 사고다발지 통과 시의 주의 멘트.
     *
     * 지금은 항상 null이다. 사고다발지 통과 판정이 아직 없어 입력에 위험구간이 들어가지 않는다. 필드만 먼저 열어 둔다.
     */
    private String dangerZoneAlert;

    /**
     * 다음 라이딩 제안.
     *
     * 지금은 항상 null이다. 과거 라이딩 이력(RAG)이 있어야 쓸 수 있는데 아직 없다.
     */
    private String nextStepSuggestion;

    public List<String> highlightsOrEmpty() {
        return highlights == null ? List.of() : highlights;
    }
}
