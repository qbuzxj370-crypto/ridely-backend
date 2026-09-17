package kr.ridely.controller;

import com.fasterxml.jackson.databind.JsonNode;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 비밀번호 변경·회원 탈퇴 통합 테스트.
 *
 * <b>둘 다 되돌릴 수 없거나 세션을 끊는 작업이라 부작용까지 본다.</b> 응답 코드만 보면 「바꿨다」는 알 수 있어도 「다른 기기가 끊겼는지」「개인 기록이 사라졌는지」는 모른다.
 *
 * 탈퇴는 FK CASCADE·SET NULL이 실제로 도는지를 DB에서 직접 센다. 스키마가 그렇게 설계됐다는 것과 그렇게 동작한다는 것은 다르다.
 */
@AutoConfigureMockMvc
class UserAccountLifecycleTest extends AbstractIntegrationTest {

    private static final String LOGIN_ID = "rider";
    private static final String PASSWORD = "Pa55word!";
    private static final String NEW_PASSWORD = "NewPa55word!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private ObjectMapper objectMapper;

    private String accessToken;
    private String refreshToken;
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

        JsonNode login = login(PASSWORD);
        accessToken = login.path("accessToken").asText();
        refreshToken = login.path("refreshToken").asText();
        userId = jdbcClient.sql("SELECT user_id FROM app_user WHERE login_id = :id")
                .param("id", LOGIN_ID).query(Long.class).single();
    }

    // ===== 비밀번호 변경 =====

    @Test
    @DisplayName("현재 비밀번호가 맞으면 바꾼다")
    void 비밀번호_변경() throws Exception {
        changePassword(PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());

        // 새 비밀번호로 로그인된다
        assertThat(login(NEW_PASSWORD).path("accessToken").asText()).isNotBlank();
    }

    @Test
    @DisplayName("바꾸면 기존 리프레시 토큰이 전부 폐기된다")
    void 변경_후_세션_종료() throws Exception {
        // 다른 기기를 흉내 낸다 - 로그인을 한 번 더 해 토큰을 둘로 만든다
        String secondRefresh = login(PASSWORD).path("refreshToken").asText();

        changePassword(PASSWORD, NEW_PASSWORD).andExpect(status().isNoContent());

        // ★ 둘 다 재발급이 안 돼야 한다. 하나라도 살아 있으면 유출된 세션이 계속 쓰인다
        refresh(refreshToken).andExpect(status().isUnauthorized());
        refresh(secondRefresh).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("현재 비밀번호가 틀리면 AUTH-201이다")
    void 현재_비밀번호_불일치() throws Exception {
        changePassword("Wrong1234!", NEW_PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH-201"));

        // 바뀌지 않았다
        assertThat(login(PASSWORD).path("accessToken").asText()).isNotBlank();
    }

    @Test
    @DisplayName("새 비밀번호가 정책에 어긋나면 AUTH-102다")
    void 정책_위반() throws Exception {
        // 회원가입과 같은 규칙이어야 한다. 가입은 되는데 변경은 안 되는 비밀번호가 생기면 안 된다
        changePassword(PASSWORD, "short")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("AUTH-102"));
    }

    // ===== 탈퇴 =====

    @Test
    @DisplayName("비밀번호가 맞으면 계정을 지운다")
    void 탈퇴() throws Exception {
        withdraw(PASSWORD).andExpect(status().isNoContent());

        assertThat(count("app_user")).isZero();
        // 로그인은 아이디가 없는 것과 같은 AUTH-201이다
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId":"%s","password":"%s"}
                                """.formatted(LOGIN_ID, PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH-201"));
    }

    @Test
    @DisplayName("개인 기록은 함께 사라지고 추천 코스는 남는다")
    void 탈퇴_시_FK_동작() throws Exception {
        // ★ 스키마가 CASCADE·SET NULL로 설계돼 있다는 것과 실제로 그렇게 도는 것은 다르다.
        //   저장 경로가 있으려면 추천 코스가 있어야 하므로 최소한의 행을 직접 넣는다
        long routeId = jdbcClient.sql("""
                        INSERT INTO recommended_route (user_id, start_geom)
                        VALUES (:userId, ST_SetSRID(ST_MakePoint(126.9339, 37.5265), 4326))
                        RETURNING recommended_route_id
                        """)
                .param("userId", userId).query(Long.class).single();
        jdbcClient.sql("""
                        INSERT INTO saved_route (user_id, recommended_route_id, custom_name)
                        VALUES (:userId, :routeId, '탈퇴 테스트')
                        """)
                .param("userId", userId).param("routeId", routeId).update();

        assertThat(count("saved_route")).isEqualTo(1);
        assertThat(count("user_settings")).isEqualTo(1);
        assertThat(count("refresh_token")).isEqualTo(1);

        withdraw(PASSWORD).andExpect(status().isNoContent());

        // CASCADE
        assertThat(count("saved_route")).isZero();
        assertThat(count("user_settings")).isZero();
        assertThat(count("refresh_token")).isZero();

        // SET NULL - 코스는 남고 주인만 끊긴다
        assertThat(count("recommended_route")).isEqualTo(1);
        Long owner = jdbcClient.sql("SELECT user_id FROM recommended_route WHERE recommended_route_id = :id")
                .param("id", routeId).query(Long.class).optional().orElse(null);
        assertThat(owner).isNull();
    }

    @Test
    @DisplayName("비밀번호가 틀리면 지우지 않는다")
    void 탈퇴_비밀번호_불일치() throws Exception {
        withdraw("Wrong1234!")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH-201"));

        assertThat(count("app_user")).isEqualTo(1);
    }

    @Test
    @DisplayName("탈퇴 뒤에도 액세스 토큰이 통과한다 — 현재 동작 기록")
    void 탈퇴_후_토큰_잔존() throws Exception {
        /*
         * JwtAuthenticationFilter는 서명·타입·만료만 보고 사용자 존재는 확인하지 않는다.
         * 액세스 토큰이 만료될 때까지(기본 15분) 탈퇴한 회원의 토큰이 인증을 통과한다.
         *
         * 이 테스트는 고칠 동작을 고정하는 것이 아니라 지금 무엇이 일어나는지 기록한다.
         * 소프트 삭제일 때는 행이 남아 조용히 넘어갔지만 하드 삭제에서는 달라진다.
         */
        withdraw(PASSWORD).andExpect(status().isNoContent());

        // 읽기 경로는 계정을 다시 찾아보므로 COMMON-004로 떨어진다
        mockMvc.perform(patch("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname":"유령"}
                                """))
                .andExpect(jsonPath("$.error.code").value("COMMON-004"));

        // 쓰기 경로는 user_id로 INSERT한다. 여기서 무엇이 나오는지가 관건이다
        mockMvc.perform(post("/api/v1/riding-sessions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(result -> assertThat(result.getResponse().getStatus())
                        .as("탈퇴한 회원의 토큰으로 쓰기를 시도했을 때의 응답")
                        .isNotEqualTo(201));
    }

    @Test
    @DisplayName("본문 없이 부르면 COMMON-001이고 계정은 그대로다")
    void 탈퇴_본문_누락() throws Exception {
        /*
         * DELETE에 본문을 싣는 설계라, 클라이언트가 기본 설정에서 본문을 빼고 보내는 일이
         * 실제로 생긴다. 그때 무엇이 나가는지 고정한다 - Swagger 설명이 이 코드를 안내한다.
         *
         * 본문이 없으면 @Valid에 닿기 전에 HttpMessageNotReadableException이 나고
         * GlobalExceptionHandler가 COMMON-001로 바꾼다.
         */
        mockMvc.perform(delete("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON-001"));

        assertThat(count("app_user")).isEqualTo(1);
    }

    @Test
    @DisplayName("비밀번호가 빈 문자열이면 COMMON-001이다")
    void 탈퇴_빈_비밀번호() throws Exception {
        // 본문은 있으나 값이 빈 경우. @NotBlank가 잡으므로 AUTH-201이 아니다
        withdraw("")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON-001"));

        assertThat(count("app_user")).isEqualTo(1);
    }

    @Test
    @DisplayName("탈퇴한 아이디로 다시 가입할 수 있다")
    void 재가입() throws Exception {
        // 개인정보를 남기지 않으므로 막을 근거가 없다. 소프트 삭제였다면 UNIQUE에 걸렸다
        withdraw(PASSWORD).andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId":"%s","password":"%s","nickname":"다시온라이더"}
                                """.formatted(LOGIN_ID, PASSWORD)))
                .andExpect(status().isCreated());
    }

    // ===== 도우미 =====

    private JsonNode login(String password) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId":"%s","password":"%s"}
                                """.formatted(LOGIN_ID, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data");
    }

    private org.springframework.test.web.servlet.ResultActions changePassword(
            String current, String next) throws Exception {
        return mockMvc.perform(patch("/api/v1/users/me/password")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"currentPassword":"%s","newPassword":"%s"}
                        """.formatted(current, next)));
    }

    private org.springframework.test.web.servlet.ResultActions withdraw(String password)
            throws Exception {
        return mockMvc.perform(delete("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"password":"%s"}
                        """.formatted(password)));
    }

    private org.springframework.test.web.servlet.ResultActions refresh(String token) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"refreshToken":"%s"}
                        """.formatted(token)));
    }

    private int count(String table) {
        return jdbcClient.sql("SELECT COUNT(*) FROM " + table).query(Integer.class).single();
    }
}
