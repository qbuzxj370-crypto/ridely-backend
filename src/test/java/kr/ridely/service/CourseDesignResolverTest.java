package kr.ridely.service;

import kr.ridely.dto.route.CandidateDTO;
import kr.ridely.dto.route.CourseDesignDTO;
import kr.ridely.dto.route.RouteCandidatesDTO;
import kr.ridely.infra.llm.CourseDesignClient;
import kr.ridely.infra.llm.FallbackCourseDesigner;
import kr.ridely.infra.llm.LlmCallException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 코스 설계 확정의 세 갈래 단위 테스트.
 *
 * DB도 LLM도 없이 분기만 본다. CourseDesignClient는 익명 서브클래스로 흉내 낸다 - AvoidSettingResolutionTest가 UserSettingsDao에 쓴 방식과 같고, 목 라이브러리를 들이지 않는다.
 *
 * <b>여기서 고정하려는 것은 「LLM이 안 돼도 코스가 나간다」와 「그 사실이 숨겨지지 않는다」 둘이다.</b> fallback 플래그가 응답의 aiProvider가 되므로, 대체해 놓고 false를 돌려주면 화면이 규칙으로 만든 문장을 AI 코치로 표시한다.
 *
 * <b>호출 횟수를 세는 이유.</b> 재시도가 조용히 늘면 Gemini RPD를 두 배로 쓴다. 게이트 1회가 추천 2회 = LLM 4~8회인데, 여기서 한 번이 두 번이 되면 그 전체가 배가 된다. 분기가 맞는지만 보면 이걸 놓친다.
 */
class CourseDesignResolverTest {

    private static final double TARGET_KM = 12.0;
    private static final BigDecimal CONVENIENCE = new BigDecimal("0.50");
    private static final BigDecimal EXERCISE = new BigDecimal("0.30");
    private static final BigDecimal SCENERY = new BigDecimal("0.20");

    private static final String TYPE_TOUR = "TOUR";
    private static final long REAL_ID = 1L;
    private static final long BOGUS_ID = 999L;

    @Test
    @DisplayName("LLM이 실재하는 경유지를 고르면 그대로 쓴다")
    void usesLlmResultWhenWaypointsExist() {
        AtomicInteger calls = new AtomicInteger();
        CourseDesignResolver resolver = resolver(calls, () -> designWith(REAL_ID));

        CourseDesignResolver.Resolved resolved = resolve(resolver);

        assertThat(resolved.fallback()).isFalse();
        assertThat(resolved.waypoints()).hasSize(1);
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("고른 것이 전부 실재하지 않으면 한 번만 다시 부른다")
    void retriesOnceWhenAllWaypointsAreBogus() {
        AtomicInteger calls = new AtomicInteger();
        // 1회차는 없는 번호, 2회차는 실재하는 번호
        CourseDesignResolver resolver = resolver(calls,
                () -> calls.get() == 1 ? designWith(BOGUS_ID) : designWith(REAL_ID));

        CourseDesignResolver.Resolved resolved = resolve(resolver);

        assertThat(resolved.fallback()).isFalse();
        assertThat(resolved.waypoints()).hasSize(1);
        // 재시도는 한 번뿐이다. 여기가 늘면 LLM 호출이 요청마다 두 배가 된다
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("다시 불러도 실재하지 않으면 규칙으로 고르고 대체를 알린다")
    void fallsBackWhenRetryAlsoBogus() {
        AtomicInteger calls = new AtomicInteger();
        CourseDesignResolver resolver = resolver(calls, () -> designWith(BOGUS_ID));

        CourseDesignResolver.Resolved resolved = resolve(resolver);

        assertThat(resolved.fallback()).isTrue();
        assertThat(resolved.waypoints()).isNotEmpty();
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("호출이 실패하면 다시 부르지 않고 규칙으로 고른다")
    void fallsBackWithoutRetryOnCallFailure() {
        AtomicInteger calls = new AtomicInteger();
        CourseDesignResolver resolver = resolver(calls, () -> {
            throw new LlmCallException("코스 설계", false, "파싱 실패", null);
        });

        CourseDesignResolver.Resolved resolved = resolve(resolver);

        assertThat(resolved.fallback()).isTrue();
        assertThat(resolved.waypoints()).isNotEmpty();
        // 재시도는 StructuredLlmCaller가 이미 한 뒤다. 여기서 또 부르면 층이 겹쳐 네 번이 된다
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("LLM이 경유지를 하나도 고르지 않으면 규칙으로 채운다")
    void fallsBackWhenLlmPicksNothing() {
        // 예전에는 그대로 진행했다. 출발지와 도착지만 남으면 순환 코스는
        // 두 점이 같아 거리가 0이 되고 ORS에서 실패한다
        AtomicInteger calls = new AtomicInteger();
        CourseDesignResolver resolver = resolver(calls, CourseDesignDTO::new);

        CourseDesignResolver.Resolved resolved = resolve(resolver);

        assertThat(resolved.fallback()).isTrue();
        assertThat(resolved.waypoints()).isNotEmpty();
        // 고른 것이 없는 것과 고른 것이 전부 가짜인 것은 다르다. 전자는 재호출하지 않는다
        assertThat(calls.get()).isEqualTo(1);
    }

    // ===== 도우미 =====

    private CourseDesignResolver.Resolved resolve(CourseDesignResolver resolver) {
        return resolver.resolve(candidates(), TARGET_KM, false, 5.0,
                CONVENIENCE, EXERCISE, SCENERY);
    }

    /**
     * 호출마다 supplier를 부르고 호출 횟수를 센다.
     *
     * 생성자 인자는 전부 null로 둔다. 이 테스트가 부르는 것은 design 하나뿐이고, 그 안을 통째로 갈아치우므로 실제 의존은 쓰이지 않는다. 쓰이면 NPE로 그 자리에서 드러난다.
     */
    private CourseDesignResolver resolver(AtomicInteger calls, Supplier<CourseDesignDTO> response) {
        CourseDesignClient client = new CourseDesignClient(null, null, null, null) {
            @Override
            public CourseDesignDTO design(RouteCandidatesDTO candidates, double targetDistanceKm,
                                          boolean circular, double straightLineKm,
                                          BigDecimal convenience, BigDecimal exercise,
                                          BigDecimal scenery) {
                calls.incrementAndGet();
                return response.get();
            }
        };
        return new CourseDesignResolver(client, new FallbackCourseDesigner());
    }

    /** 관광지 후보 하나. 규칙 대체가 집을 것이 있어야 한다 */
    private RouteCandidatesDTO candidates() {
        CandidateDTO tour = new CandidateDTO(TYPE_TOUR, REAL_ID, "선유도공원",
                37.5432, 126.8997, 120);
        tour.setProgressRatio(0.4);

        RouteCandidatesDTO candidates = new RouteCandidatesDTO();
        candidates.setTours(List.of(tour));
        return candidates;
    }

    private CourseDesignDTO designWith(long waypointId) {
        CourseDesignDTO design = new CourseDesignDTO();
        design.setSelectedWaypoints(List.of(
                new CourseDesignDTO.SelectedWaypoint(TYPE_TOUR, waypointId, "강이 보인다")));
        design.setDesignIntent("한강을 따라 도는 코스");
        return design;
    }
}
