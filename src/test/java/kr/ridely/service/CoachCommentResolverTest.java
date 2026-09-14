package kr.ridely.service;

import kr.ridely.dto.route.CoachCommentDTO;
import kr.ridely.dto.route.CourseDesignDTO;
import kr.ridely.dto.route.PassingDangerZoneDTO;
import kr.ridely.dto.route.RouteCandidatesDTO;
import kr.ridely.infra.llm.CoachCommentClient;
import kr.ridely.infra.llm.DangerAlertObserver;
import kr.ridely.infra.llm.LlmCallException;
import kr.ridely.infra.llm.LlmProperties;
import kr.ridely.infra.llm.TemplateCommentFactory;
import kr.ridely.infra.llm.ToneValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 코치 코멘트 확정 단위 테스트.
 *
 * <b>여기서 고정하려는 것은 「톤 위반에 재호출을 두지 않는다」다.</b> 설계 문서는 「commentate 재호출 → 템플릿」이었으나 DB에 쌓인 코멘트 46건에 위반이 0건이라 중간 단계를 뺐다. 누군가 설계 문서를 보고 재호출을 되살리면 호출 횟수 검사가 깨진다.
 *
 * 위반 문구는 application.yml의 tone-violation-patterns와 같은 값을 쓴다. 여기에 실제 설정을 주입하지 않는 이유는 yml을 고쳐 문구를 바꿔도 이 테스트가 깨지면 안 되기 때문이다 - 검사하려는 것은 문구 목록이 아니라 <b>검출됐을 때의 처리</b>다.
 */
class CoachCommentResolverTest {

    private static final double TARGET_KM = 12.0;
    private static final double DISTANCE_KM = 12.4;
    private static final int DURATION_MIN = 46;
    private static final String INTENSITY = "MODERATE";
    private static final int WAYPOINT_COUNT = 3;

    private static final String VIOLATION = "드립니다";

    @Test
    @DisplayName("톤을 지킨 코멘트는 그대로 쓴다")
    void keepsCleanComment() {
        AtomicInteger calls = new AtomicInteger();
        CoachCommentResolver resolver = resolver(calls, () -> comment("한강 따라 시원하게 달려보자"));

        CoachCommentResolver.Resolved resolved = resolve(resolver, List.of());

        assertThat(resolved.fallback()).isFalse();
        assertThat(resolved.comment().getCoachComment()).isEqualTo("한강 따라 시원하게 달려보자");
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("톤을 벗어나면 다시 부르지 않고 템플릿으로 바꾼다")
    void replacesWithTemplateWithoutRetry() {
        AtomicInteger calls = new AtomicInteger();
        CoachCommentResolver resolver = resolver(calls,
                () -> comment("즐거운 라이딩 되시길 바라며 안내" + VIOLATION));

        CoachCommentResolver.Resolved resolved = resolve(resolver, List.of());

        assertThat(resolved.fallback()).isTrue();
        assertThat(resolved.comment().getCoachComment()).doesNotContain(VIOLATION);
        // 위반이 46건 중 0건이라 LLM을 한 번 더 부를 값이 없다. 재호출을 되살리면 여기가 깨진다
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("코치 코멘트가 아닌 필드의 위반도 잡는다")
    void detectsViolationOutsideCoachComment() {
        // 화면에서는 나란히 보이므로 한 곳만 톤이 다르면 그게 더 눈에 띈다
        AtomicInteger calls = new AtomicInteger();
        CoachCommentResolver resolver = resolver(calls, () -> {
            CoachCommentDTO comment = comment("한강 따라 달려보자");
            comment.setNextStepSuggestion("다음에는 16km에 도전하시기 바랍니다");
            return comment;
        });

        CoachCommentResolver.Resolved resolved = resolve(resolver, List.of());

        assertThat(resolved.fallback()).isTrue();
    }

    @Test
    @DisplayName("위험 안내가 비어도 LLM 코멘트를 그대로 내보낸다")
    void keepsCommentEvenWhenDangerAlertBlank() {
        // 한때 이 경우에 코멘트를 통째로 템플릿으로 바꿨다. 지금은 세기만 한다 -
        // 라이더에게 위치를 알리는 것은 지도와 GPS 근접 알림의 일이다.
        // 빈도가 쌓이면 그때 프롬프트를 고칠지 필드를 채울지 정한다
        AtomicInteger calls = new AtomicInteger();
        CoachCommentResolver resolver = resolver(calls, () -> {
            CoachCommentDTO comment = comment("한강 따라 달려보자");
            comment.setDangerZoneAlert(null);
            return comment;
        });

        CoachCommentResolver.Resolved resolved = resolve(resolver,
                List.of(zone("서울 동작구 본동(한강대교남단교차로 부근)", "WARNING")));

        assertThat(resolved.fallback()).isFalse();
        assertThat(resolved.comment().getCoachComment()).isEqualTo("한강 따라 달려보자");
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("지점명을 줄여 써도 대체하지 않는다")
    void keepsCommentWhenZoneNameShortened() {
        // spot_name은 「서울 동작구 본동(한강대교남단교차로 부근)」이고 LLM은
        // 「한강대교 남단」으로 줄여 쓴다. 2026-09-13에 이 경우를 결함으로 오인해
        // 멀쩡한 코멘트를 버렸다
        AtomicInteger calls = new AtomicInteger();
        CoachCommentResolver resolver = resolver(calls, () -> {
            CoachCommentDTO comment = comment("한강 따라 달려보자");
            comment.setDangerZoneAlert("3km 지점 한강대교 남단은 살피면서 지나가요");
            return comment;
        });

        CoachCommentResolver.Resolved resolved = resolve(resolver,
                List.of(zone("서울 동작구 본동(한강대교남단교차로 부근)", "WARNING")));

        assertThat(resolved.fallback()).isFalse();
        assertThat(resolved.comment().getDangerZoneAlert()).contains("한강대교 남단");
    }

    @Test
    @DisplayName("호출이 실패하면 템플릿으로 바꾼다")
    void replacesWithTemplateOnCallFailure() {
        AtomicInteger calls = new AtomicInteger();
        CoachCommentResolver resolver = resolver(calls, () -> {
            throw new LlmCallException("코치 코멘트", true, "15초 초과", null);
        });

        CoachCommentResolver.Resolved resolved = resolve(resolver, List.of());

        assertThat(resolved.fallback()).isTrue();
        assertThat(resolved.comment().getCoachComment()).contains("12.4km");
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("대체해도 위험 구역 안내는 빠지지 않는다")
    void templateKeepsDangerAlert() {
        // 코멘트 품질은 타협해도 안전 정보는 아니다. 회피를 끈 사용자가
        // 경고 없이 그 구간을 지나게 된다
        AtomicInteger calls = new AtomicInteger();
        CoachCommentResolver resolver = resolver(calls, () -> {
            throw new LlmCallException("코치 코멘트", false, "파싱 실패", null);
        });

        CoachCommentResolver.Resolved resolved = resolve(resolver, List.of(zone("한강대교 남단", "WARNING")));

        assertThat(resolved.comment().getDangerZoneAlert())
                .contains("한강대교 남단")
                .contains("경고");
    }

    // ===== 도우미 =====

    private CoachCommentResolver.Resolved resolve(CoachCommentResolver resolver,
                                                  List<PassingDangerZoneDTO> zones) {
        return resolver.resolve(new CourseDesignDTO(), new RouteCandidatesDTO(),
                TARGET_KM, false, DISTANCE_KM, DURATION_MIN, INTENSITY,
                zones, false, WAYPOINT_COUNT);
    }

    /** 생성자 인자를 전부 null로 둔다. generate를 통째로 갈아치우므로 실제 의존은 쓰이지 않는다 */
    private CoachCommentResolver resolver(AtomicInteger calls, Supplier<CoachCommentDTO> response) {
        CoachCommentClient client = new CoachCommentClient(null, null, null, null) {
            @Override
            public CoachCommentDTO generate(CourseDesignDTO design, RouteCandidatesDTO candidates,
                                            double targetDistanceKm, boolean circular,
                                            double totalDistanceKm, int durationMin,
                                            String intensityLevel,
                                            List<PassingDangerZoneDTO> dangerZones,
                                            boolean avoidApplied) {
                calls.incrementAndGet();
                return response.get();
            }
        };
        return new CoachCommentResolver(client, toneValidator(),
                new DangerAlertObserver(), new TemplateCommentFactory());
    }

    private ToneValidator toneValidator() {
        return new ToneValidator(new LlmProperties(
                "gemini", 15, 1, 0.2, 0.6, List.of(VIOLATION, "하시기 바랍니다", "이용자")));
    }

    private CoachCommentDTO comment(String coachComment) {
        CoachCommentDTO comment = new CoachCommentDTO();
        comment.setTitle("한강 12km 코스");
        comment.setCoachComment(coachComment);
        comment.setHighlights(List.of("선유도공원 경유"));
        comment.setNextStepSuggestion("다음엔 16km 어때");
        return comment;
    }

    private PassingDangerZoneDTO zone(String spotName, String dangerLevel) {
        PassingDangerZoneDTO zone = new PassingDangerZoneDTO();
        zone.setSpotName(spotName);
        zone.setDangerLevel(dangerLevel);
        zone.setOccurrenceCount(8);
        return zone;
    }
}
