package kr.ridely.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.ridely.config.RouteProperties;
import kr.ridely.dao.RecommendationCacheDao;
import kr.ridely.dto.route.RouteRecommendRequestDTO;
import kr.ridely.dto.route.RouteRecommendResponseDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 추천 응답 멱등성 단위 테스트.
 *
 * DB 없이 분기만 본다. {@link RecommendationCacheDao}는 인메모리 맵으로 흉내 낸다 - 목 라이브러리를 들이지 않고 {@code AvoidSettingResolutionTest}가 {@code UserSettingsDao}에 쓴 방식과 맞춘다.
 *
 * <b>여기서 고정하려는 것은 「요청대로 처리되지 않은 응답은 저장하지 않는다」다.</b> 재시도로 대체 응답을 다시 받느니 새로 시도해 제대로 된 것을 받는 편이 낫고, 회피 쪽은 사용자가 켠 안전 기능이라 더 그렇다.
 *
 * 만료는 여기서 다루지 않는다. SQL의 {@code expires_at > NOW()}가 판정하므로 DAO를 흉내 내는 순간 그 조건이 사라진다. 만료된 행을 돌려주지 않는 것은 매퍼의 책임이다.
 */
class RecommendationReplayStoreTest {

    private static final String CLIENT_KEY = "550e8400-e29b-41d4-a716-446655440000";
    private static final long USER_ID = 7L;
    private static final int TTL_MINUTES = 20;

    private final Map<String, String> stored = new HashMap<>();
    private final RecommendationReplayStore store = new RecommendationReplayStore(
            fakeDao(), new ObjectMapper(), properties());

    @Test
    @DisplayName("같은 키로 다시 오면 첫 응답을 돌려준다")
    void replaysStoredResponse() {
        RouteRecommendRequestDTO request = request();
        store.remember(CLIENT_KEY, USER_ID, request, response("gemini", true), true);

        Optional<RouteRecommendResponseDTO> replayed = store.replay(CLIENT_KEY, USER_ID, request);

        assertThat(replayed).isPresent();
        assertThat(replayed.get().getRecommendedRouteId()).isEqualTo(120L);
    }

    @Test
    @DisplayName("키가 없으면 저장도 조회도 하지 않는다")
    void withoutKeyNothingHappens() {
        // 헤더를 안 보내는 클라이언트도 그대로 동작해야 한다
        store.remember(null, USER_ID, request(), response("gemini", true), false);

        assertThat(stored).isEmpty();
        assertThat(store.replay(null, USER_ID, request())).isEmpty();
    }

    @Test
    @DisplayName("다른 사용자는 같은 키를 보내도 남의 응답을 못 본다")
    void otherUserCannotReplay() {
        store.remember(CLIENT_KEY, USER_ID, request(), response("gemini", true), false);

        assertThat(store.replay(CLIENT_KEY, 8L, request())).isEmpty();
    }

    @Test
    @DisplayName("규칙으로 만든 응답은 저장하지 않는다")
    void doesNotStoreFallback() {
        // 재시도로 대체 응답을 다시 받느니 새로 시도해 제대로 된 것을 받는 편이 낫다
        store.remember(CLIENT_KEY, USER_ID, request(), response("FALLBACK", true), false);

        assertThat(stored).isEmpty();
    }

    @Test
    @DisplayName("회피를 요청했는데 적용되지 않으면 저장하지 않는다")
    void doesNotStoreFailedAvoidance() {
        // 사고다발지가 유일한 통로라 실패하는 경우가 있는데 경유지 조합이 매번 달라
        // 결정적이지 않다. 저장하면 다음에 성공할 기회를 없앤다
        store.remember(CLIENT_KEY, USER_ID, request(), response("gemini", false), true);

        assertThat(stored).isEmpty();
    }

    @Test
    @DisplayName("회피를 요청하지 않았으면 적용되지 않아도 저장한다")
    void storesWhenAvoidanceNotRequested() {
        // avoidDangerZonesApplied가 false인 이유가 둘이다 - 안 켰거나, 켰는데 실패했거나.
        // 결과만 보면 구분되지 않아 요청 여부를 따로 받는다
        store.remember(CLIENT_KEY, USER_ID, request(), response("gemini", false), false);

        assertThat(stored).hasSize(1);
    }

    @Test
    @DisplayName("같은 키에 다른 요청이 오면 못 본 것으로 친다")
    void differentRequestIsNotReplayed() {
        store.remember(CLIENT_KEY, USER_ID, request(), response("gemini", true), false);

        RouteRecommendRequestDTO other = request();
        other.setTargetDistanceKm(new BigDecimal("20.0"));

        assertThat(store.replay(CLIENT_KEY, USER_ID, other)).isEmpty();
    }

    @Test
    @DisplayName("요청 DTO에 equals가 없어도 같은 내용을 알아본다")
    void recognisesEqualRequestWithoutEquals() {
        // DTO 규약이 class + Lombok(@Getter @Setter @NoArgsConstructor @AllArgsConstructor)이라
        // equals가 없다. 동일성으로 비교하면 영영 false가 되어 저장해 둔 응답을 못 꺼낸다
        store.remember(CLIENT_KEY, USER_ID, request(), response("gemini", true), false);

        assertThat(store.replay(CLIENT_KEY, USER_ID, request())).isPresent();
    }

    @Test
    @DisplayName("저장된 값이 깨져 있어도 추천은 계속된다")
    void brokenPayloadFallsThrough() {
        // 저장 형식이 바뀌면 옛 행을 못 읽는다. 그때 500이 나가면 안 된다
        stored.put(kr.ridely.common.IdempotencyKey.of(CLIENT_KEY, USER_ID), "{깨진 값");

        assertThat(store.replay(CLIENT_KEY, USER_ID, request())).isEmpty();
    }

    // ===== 도우미 =====

    /** 인메모리 맵. 만료는 SQL의 책임이라 여기서 흉내 내지 않는다 */
    private RecommendationCacheDao fakeDao() {
        return new RecommendationCacheDao() {

            @Override
            public String selectValidPayload(String cacheKey) {
                return stored.get(cacheKey);
            }

            @Override
            public int upsert(String cacheKey, String payload, int ttlMinutes) {
                stored.put(cacheKey, payload);
                return 1;
            }
        };
    }

    /** TTL만 쓰인다. 나머지는 이 클래스가 건드리지 않으므로 아무 값이나 둔다 */
    private RouteProperties properties() {
        return new RouteProperties(1500, 5.0, null, 5, 100, TTL_MINUTES,
                100.0, 200, List.of("DANGER", "WARNING"));
    }

    private RouteRecommendRequestDTO request() {
        RouteRecommendRequestDTO request = new RouteRecommendRequestDTO();
        request.setStartLat(37.5265);
        request.setStartLng(126.9339);
        request.setEndLat(37.5120);
        request.setEndLng(127.0050);
        request.setTargetDistanceKm(new BigDecimal("12.6"));
        request.setPriorityConvenience(new BigDecimal("0.50"));
        request.setPriorityExercise(new BigDecimal("0.30"));
        request.setPriorityScenery(new BigDecimal("0.20"));
        return request;
    }

    private RouteRecommendResponseDTO response(String provider, boolean avoidApplied) {
        RouteRecommendResponseDTO response = new RouteRecommendResponseDTO();
        response.setRecommendedRouteId(120L);
        response.setAiProvider(provider);
        response.setAvoidDangerZonesApplied(avoidApplied);
        response.setTotalDistanceKm(new BigDecimal("12.4"));
        return response;
    }
}
