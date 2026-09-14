package kr.ridely.infra.llm;

import kr.ridely.dto.route.CoachCommentDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Stream;

/**
 * 코치 코멘트가 Coach Ridely 톤을 벗어났는지 본다.
 *
 * 판정 문구는 {@code ridely.ai.llm.tone-violation-patterns}에 있다. 설계는 {@code resources/prompts/tone-blacklist.txt}였으나 yml을 정본으로 삼았다 - 지금 문구가 셋뿐이라 파일을 따로 두면 정본이 둘이 되고, yml은 프로파일별로 덮어쓸 수 있다.
 *
 * <b>걸려도 재호출하지 않는다.</b> 설계는 「commentate 재호출 → 템플릿」이었는데 중간 단계를 뺐다. DB에 쌓인 코멘트 46건 전부에 이 문구를 걸어 본 결과 위반이 0건이었다(2026-09-13). 이 빈도에 LLM을 한 번 더 부르면 쿼터와 응답 시간만 쓴다. 걸리면 {@link TemplateCommentFactory}로 바로 바꾼다.
 *
 * ⚠️ 0건은 「나지 않는다」가 아니라 「46번 중엔 없었다」다. 참값 상한은 대략 6% 언저리로 봐야 한다. 검증기를 만들되 재호출을 뺀 것이 그 균형이다.
 *
 * <b>단순 문자열 포함으로 본다.</b> 설계는 정규식이라 했으나 지금 문구가 「드립니다」처럼 어미 조각이라 정규식이 얻는 것이 없다. 패턴이 늘어 문맥 구분이 필요해지면 그때 바꾼다.
 */
@Component
public class ToneValidator {

    private static final Logger log = LoggerFactory.getLogger(ToneValidator.class);

    private final List<String> patterns;

    public ToneValidator(LlmProperties properties) {
        this.patterns = properties.toneViolationPatterns() == null
                ? List.of() : properties.toneViolationPatterns();
    }

    /**
     * 코멘트에 위반 문구가 있는지 본다.
     *
     * 검사 대상은 <b>사용자에게 문장으로 보이는 필드 전부</b>다. 코치 코멘트만 보면 위험 구역 안내나 다음 단계 제안에 든 존댓말을 놓친다 - 화면에서는 나란히 보이므로 한 곳만 톤이 다르면 그게 더 눈에 띈다.
     *
     * @return 걸린 문구. 없으면 null
     */
    public String findViolation(CoachCommentDTO comment) {
        if (comment == null || patterns.isEmpty()) {
            return null;
        }
        List<String> texts = Stream.concat(
                        Stream.of(comment.getTitle(), comment.getCoachComment(),
                                comment.getDangerZoneAlert(), comment.getNextStepSuggestion()),
                        comment.highlightsOrEmpty().stream())
                .filter(t -> t != null && !t.isBlank())
                .toList();

        for (String pattern : patterns) {
            for (String text : texts) {
                if (text.contains(pattern)) {
                    log.warn("코치 코멘트가 톤을 벗어났다. 문구={}", pattern);
                    return pattern;
                }
            }
        }
        return null;
    }
}
