package kr.ridely.dao;

import kr.ridely.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 멱등성 저장소 매퍼 SQL 통합 테스트.
 *
 * <b>단위 테스트가 못 보는 세 가지를 본다.</b> {@code RecommendationReplayStoreTest}는 DAO를 인메모리 맵으로 흉내 내는데, 맵에는 만료도 없고 덮어쓰기는 당연히 되며 JSONB도 없다. 여기 SQL은 셋 다 실제로 한다.
 *
 * <pre>
 * expires_at &gt; NOW()            만료된 행을 걸러낸다
 * ON CONFLICT DO UPDATE          같은 키가 다시 오면 덮어쓴다
 * CAST(#{payload} AS jsonb)      ::jsonb 표기는 MyBatis 파라미터 파서가 깨뜨린다
 * </pre>
 *
 * ⚠️ <b>{@code ON CONFLICT}는 빠져도 평소엔 멀쩡하다.</b> 새 키는 항상 INSERT라 잘 들어간다. 만료된 키가 다시 올 때만 UNIQUE 위반으로 500이 나는데, TTL이 20분이라 실사용에서 드물고 손으로 돌려서는 걸리지 않는다.
 */
class RecommendationCacheMapperTest extends AbstractIntegrationTest {

    private static final String KEY = "a".repeat(64);

    @Autowired
    private RecommendationCacheDao dao;

    @Autowired
    private JdbcClient jdbcClient;

    @BeforeEach
    void clear() {
        jdbcClient.sql("TRUNCATE recommendation_cache").update();
    }

    @Test
    @DisplayName("저장한 응답을 그대로 꺼낸다")
    void storesAndReads() {
        dao.upsert(KEY, "{\"a\":1}", 20);

        assertThat(dao.selectValidPayload(KEY)).isNotNull();
    }

    @Test
    @DisplayName("만료된 행은 나오지 않는다")
    void expiredRowIsHidden() {
        // 음수 TTL이면 expires_at이 과거가 된다. 행은 남아 있지만 조회에 걸리지 않는다
        dao.upsert(KEY, "{\"a\":1}", -1);

        assertThat(dao.selectValidPayload(KEY)).isNull();
        assertThat(jdbcClient.sql("SELECT COUNT(*) FROM recommendation_cache")
                .query(Integer.class).single()).isEqualTo(1);
    }

    @Test
    @DisplayName("만료된 키로 다시 저장해도 터지지 않고 덮어쓴다")
    void expiredKeyCanBeReused() {
        // ON CONFLICT가 없으면 두 번째 호출이 UNIQUE 위반으로 죽는다.
        // 그러면 20분 뒤 같은 키로 재시도한 사용자가 500을 받는다
        dao.upsert(KEY, "{\"first\":true}", -1);
        dao.upsert(KEY, "{\"second\":true}", 20);

        assertThat(dao.selectValidPayload(KEY)).contains("second");
        assertThat(jdbcClient.sql("SELECT COUNT(*) FROM recommendation_cache")
                .query(Integer.class).single()).isEqualTo(1);
    }

    @Test
    @DisplayName("한글과 따옴표가 든 JSON이 그대로 돌아온다")
    void jsonbRoundTripsKoreanAndQuotes() {
        // 코치 코멘트에 한글과 따옴표가 들어간다. CAST를 ::jsonb로 바꾸면
        // MyBatis가 :jsonb를 파라미터로 오인해 여기서 깨진다
        String payload = "{\"comment\":\"한강 따라 달려봐요\",\"quoted\":\"그는 \\\"좋다\\\"고 했다\"}";

        dao.upsert(KEY, payload, 20);
        String read = dao.selectValidPayload(KEY);

        assertThat(read).contains("한강 따라 달려봐요");
        assertThat(read).contains("좋다");
    }

    @Test
    @DisplayName("없는 키는 null이다")
    void unknownKeyIsNull() {
        assertThat(dao.selectValidPayload("b".repeat(64))).isNull();
    }
}
