package kr.ridely.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.ridely.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 라이딩 누적 통계 통합 테스트.
 *
 * <b>집계는 무엇을 세느냐가 전부라 경계를 직접 만들어 확인한다.</b> 합계가 맞는지만 보면 「달리는 중인 세션을 뺐는지」「자유 주행을 거리에는 넣고 강도에서는 뺐는지」가 드러나지 않는다.
 *
 * 세션은 API로 만들고 측정값은 SQL로 넣는다. 종료 API는 GPS 트랙을 함께 받아 PostGIS를 타는데, 여기서 보려는 것은 집계라 거리·속도·강도만 있으면 된다.
 */
@AutoConfigureMockMvc
class RidingSummaryTest extends AbstractIntegrationTest {

    private static final String LOGIN_ID = "rider";
    private static final String PASSWORD = "Pa55word!";
    private static final String SUMMARY_URL = "/api/v1/riding-sessions/summary";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private ObjectMapper objectMapper;

    private String accessToken;
    private long userId;

    @BeforeEach
    void 회원가입_후_로그인() throws Exception {
        jdbcClient.sql("TRUNCATE TABLE app_user RESTART IDENTITY CASCADE").update();
        jdbcClient.sql("TRUNCATE TABLE recommended_route RESTART IDENTITY CASCADE").update();

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId":"%s","password":"%s","nickname":"라이더"}
                                """.formatted(LOGIN_ID, PASSWORD)))
                .andExpect(status().isCreated());

        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId":"%s","password":"%s"}
                                """.formatted(LOGIN_ID, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        accessToken = objectMapper.readTree(body).path("data").path("accessToken").asText();

        userId = jdbcClient.sql("SELECT user_id FROM app_user WHERE login_id = :id")
                .param("id", LOGIN_ID).query(Long.class).single();
    }

    @Test
    @DisplayName("기록이 없으면 0으로 채운 200이다")
    void 기록_없음() throws Exception {
        // 가입 직후가 이 상태다. 404로 내면 화면이 에러 분기를 만들어야 한다
        summary()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalRideCount").value(0))
                .andExpect(jsonPath("$.data.totalDistanceKm").value(0))
                .andExpect(jsonPath("$.data.avgSpeedKmh").doesNotExist())
                .andExpect(jsonPath("$.data.lightCount").value(0))
                .andExpect(jsonPath("$.data.challengeCount").value(0));
    }

    @Test
    @DisplayName("종료된 라이딩만 세고 달리는 중인 것은 빠진다")
    void 진행_중_제외() throws Exception {
        endedSession(null, "10.0", "15.0");
        endedSession(null, "20.0", "17.0");
        runningSession();   // ★ 이것이 섞이면 횟수가 3, 평균이 낮아진다

        summary()
                .andExpect(jsonPath("$.data.totalRideCount").value(2))
                .andExpect(jsonPath("$.data.totalDistanceKm").value(30.0))
                .andExpect(jsonPath("$.data.avgSpeedKmh").value(16.0));
    }

    @Test
    @DisplayName("강도별로 나눠 세고 합이 총 횟수와 같다")
    void 강도_분포() throws Exception {
        endedSession(route("LIGHT"), "5.0", "12.0");
        endedSession(route("MODERATE"), "14.0", "16.0");
        endedSession(route("MODERATE"), "15.0", "16.0");
        endedSession(route("HARD"), "30.0", "20.0");

        summary()
                .andExpect(jsonPath("$.data.totalRideCount").value(4))
                .andExpect(jsonPath("$.data.lightCount").value(1))
                .andExpect(jsonPath("$.data.moderateCount").value(2))
                .andExpect(jsonPath("$.data.hardCount").value(1))
                .andExpect(jsonPath("$.data.challengeCount").value(0));
    }

    @Test
    @DisplayName("자유 주행은 거리에 들어가고 강도 분포에서만 빠진다")
    void 자유_주행() throws Exception {
        /*
         * ★ 이 테스트가 LEFT JOIN을 고정한다. INNER JOIN으로 바꾸면 자유 주행이 통째로 빠져
         *   누적 거리가 14.0으로 줄고 총 횟수도 1이 된다.
         */
        endedSession(route("MODERATE"), "14.0", "16.0");
        endedSession(null, "6.0", "14.0");   // 추천 없이 달린 기록

        summary()
                .andExpect(jsonPath("$.data.totalRideCount").value(2))
                .andExpect(jsonPath("$.data.totalDistanceKm").value(20.0))
                // 네 등급의 합은 1이다. 총 횟수 2와의 차이가 자유 주행 횟수다
                .andExpect(jsonPath("$.data.moderateCount").value(1))
                .andExpect(jsonPath("$.data.lightCount").value(0))
                .andExpect(jsonPath("$.data.hardCount").value(0))
                .andExpect(jsonPath("$.data.challengeCount").value(0));
    }

    @Test
    @DisplayName("남의 기록은 섞이지 않는다")
    void 다른_회원_격리() throws Exception {
        endedSession(null, "10.0", "15.0");

        // 다른 회원의 기록을 같은 테이블에 넣는다
        long otherId = jdbcClient.sql("""
                        INSERT INTO app_user (login_id, password_hash, nickname)
                        VALUES ('other', 'x', '남')
                        RETURNING user_id
                        """).query(Long.class).single();
        jdbcClient.sql("""
                        INSERT INTO riding_session (user_id, started_at, ended_at, distance_km, avg_speed_kmh)
                        VALUES (:userId, NOW(), NOW(), 999.0, 99.0)
                        """)
                .param("userId", otherId).update();

        summary()
                .andExpect(jsonPath("$.data.totalRideCount").value(1))
                .andExpect(jsonPath("$.data.totalDistanceKm").value(10.0));
    }

    @Test
    @DisplayName("토큰이 없으면 COMMON-002다")
    void 토큰_없음() throws Exception {
        mockMvc.perform(get(SUMMARY_URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("COMMON-002"));
    }

    // ===== 도우미 =====

    private ResultActions summary() throws Exception {
        return mockMvc.perform(get(SUMMARY_URL)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));
    }

    /** 강도를 가진 추천 코스를 만들고 번호를 돌려준다 */
    private long route(String intensityLevel) {
        return jdbcClient.sql("""
                        INSERT INTO recommended_route (user_id, intensity_level, start_geom)
                        VALUES (:userId, :intensity,
                                ST_SetSRID(ST_MakePoint(126.9339, 37.5265), 4326))
                        RETURNING recommended_route_id
                        """)
                .param("userId", userId)
                .param("intensity", intensityLevel)
                .query(Long.class).single();
    }

    /** 종료된 세션 하나. routeId가 null이면 자유 주행 */
    private void endedSession(Long routeId, String distanceKm, String avgSpeedKmh) {
        jdbcClient.sql("""
                        INSERT INTO riding_session
                               (user_id, recommended_route_id, started_at, ended_at,
                                distance_km, avg_speed_kmh, is_completed)
                        VALUES (:userId, :routeId, NOW() - INTERVAL '1 hour', NOW(),
                                CAST(:distance AS NUMERIC), CAST(:speed AS NUMERIC), TRUE)
                        """)
                .param("userId", userId)
                .param("routeId", routeId)
                .param("distance", distanceKm)
                .param("speed", avgSpeedKmh)
                .update();
    }

    /** 달리는 중인 세션. ended_at이 비어 있고 측정값도 없다 */
    private void runningSession() {
        jdbcClient.sql("""
                        INSERT INTO riding_session (user_id, started_at)
                        VALUES (:userId, NOW())
                        """)
                .param("userId", userId).update();
    }
}
