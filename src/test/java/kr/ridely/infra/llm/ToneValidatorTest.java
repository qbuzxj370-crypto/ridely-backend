package kr.ridely.infra.llm;

import kr.ridely.dto.route.CoachCommentDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 코치 코멘트 톤 검증 단위 테스트.
 *
 * <b>검사 대상이 코치 코멘트 하나가 아니라는 것을 고정한다.</b> 화면에서는 제목·하이라이트·위험 안내·다음 제안이 나란히 보이므로, 한 곳만 존댓말이면 오히려 더 눈에 띈다.
 *
 * 판정 문구는 테스트가 직접 준다. application.yml을 읽으면 문구를 바꿀 때마다 테스트가 깨지는데, 여기서 보려는 것은 문구 목록이 아니라 <b>훑는 범위</b>다.
 */
class ToneValidatorTest {

    private static final String VIOLATION = "드립니다";
    private static final List<String> PATTERNS = List.of(VIOLATION, "시기 바랍니다", "이용자");

    @Test
    @DisplayName("반말 코멘트는 통과한다")
    void passesCleanComment() {
        assertThat(validator(PATTERNS).findViolation(comment(c ->
                c.setCoachComment("한강 따라 시원하게 달려보자"))))
                .isNull();
    }

    @Test
    @DisplayName("코치 코멘트의 위반을 잡는다")
    void detectsInCoachComment() {
        assertThat(validator(PATTERNS).findViolation(comment(c ->
                c.setCoachComment("좋은 코스를 추천해" + VIOLATION))))
                .isEqualTo(VIOLATION);
    }

    @Test
    @DisplayName("제목·하이라이트·위험 안내·다음 제안의 위반도 잡는다")
    void detectsInEveryVisibleField() {
        assertThat(validator(PATTERNS).findViolation(comment(c ->
                c.setTitle("코스를 안내" + VIOLATION)))).isNotNull();

        assertThat(validator(PATTERNS).findViolation(comment(c ->
                c.setHighlights(List.of("선유도공원 경유", "급수대를 안내" + VIOLATION))))).isNotNull();

        assertThat(validator(PATTERNS).findViolation(comment(c ->
                c.setDangerZoneAlert("사고다발지를 조심하시기 바랍니다")))).isNotNull();

        assertThat(validator(PATTERNS).findViolation(comment(c ->
                c.setNextStepSuggestion("이용자님께 16km를 권해" + VIOLATION)))).isNotNull();
    }

    @Test
    @DisplayName("「~시기 바랍니다」의 앞 글자가 달라도 잡는다")
    void catchesVariantsOfPoliteRequest() {
        // 처음에는 "하시기 바랍니다"였다. 앞 글자를 고정하면 아래 셋이 전부 빠져나간다
        assertThat(validator(PATTERNS).findViolation(comment(c ->
                c.setCoachComment("도전해 보시기 바랍니다")))).isNotNull();
        assertThat(validator(PATTERNS).findViolation(comment(c ->
                c.setCoachComment("참고해 주시기 바랍니다")))).isNotNull();
        assertThat(validator(PATTERNS).findViolation(comment(c ->
                c.setCoachComment("천천히 가시기 바랍니다")))).isNotNull();
    }

    @Test
    @DisplayName("판정 문구가 없으면 아무것도 걸리지 않는다")
    void passesWhenNoPatterns() {
        // 설정을 비워 검증을 끌 수 있어야 한다
        assertThat(validator(List.of()).findViolation(comment(c ->
                c.setCoachComment("안내" + VIOLATION)))).isNull();
        assertThat(validator(null).findViolation(comment(c ->
                c.setCoachComment("안내" + VIOLATION)))).isNull();
    }

    @Test
    @DisplayName("코멘트가 없거나 필드가 비어 있어도 터지지 않는다")
    void handlesNulls() {
        assertThat(validator(PATTERNS).findViolation(null)).isNull();
        assertThat(validator(PATTERNS).findViolation(new CoachCommentDTO())).isNull();
    }

    private ToneValidator validator(List<String> patterns) {
        return new ToneValidator(new LlmProperties("gemini", 15, 1, 0.2, 0.6, 2048, patterns, 150));
    }

    /** 나머지 필드는 깨끗하게 두고 하나만 오염시킨다 */
    private CoachCommentDTO comment(java.util.function.Consumer<CoachCommentDTO> taint) {
        CoachCommentDTO comment = new CoachCommentDTO();
        comment.setTitle("한강 12km 코스");
        comment.setCoachComment("한강 따라 달려보자");
        comment.setHighlights(List.of("선유도공원 경유"));
        comment.setNextStepSuggestion("다음엔 16km 어때");
        taint.accept(comment);
        return comment;
    }
}
