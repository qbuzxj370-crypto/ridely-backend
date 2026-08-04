package kr.ridely.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.ridely.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 로그인·토큰 재발급·로그아웃 통합 테스트.
 *
 * 토큰 수명주기 전체를 실제 DB와 함께 검증한다.
 */
@AutoConfigureMockMvc
class AuthLoginControllerTest extends AbstractIntegrationTest {

    private static final String SIGNUP_URL = "/api/v1/auth/signup";
    private static final String LOGIN_URL = "/api/v1/auth/login";
    private static final String REFRESH_URL = "/api/v1/auth/refresh";
    private static final String LOGOUT_URL = "/api/v1/auth/logout";

    private static final String LOGIN_ID = "rider";
    private static final String PASSWORD = "Pa55word!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void 회원_준비() throws Exception {
        jdbcClient.sql("TRUNCATE TABLE app_user RESTART IDENTITY CASCADE").update();

        mockMvc.perform(post(SIGNUP_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId":"%s","password":"%s","nickname":"라이더"}
                                """.formatted(LOGIN_ID, PASSWORD)))
                .andExpect(status().isCreated());
    }

    /** 로그인 후 토큰 묶음을 돌려준다 */
    private JsonNode 로그인() throws Exception {
        String body = mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId":"%s","password":"%s"}
                                """.formatted(LOGIN_ID, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data");
    }

    private String 토큰_본문(String token) {
        return """
                {"refreshToken":"%s"}
                """.formatted(token);
    }

    @Test
    @DisplayName("로그인하면 액세스·리프레시 토큰을 발급한다")
    void 로그인_성공() throws Exception {
        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId":"%s","password":"%s"}
                                """.formatted(LOGIN_ID, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.accessTokenExpiresIn").isNumber());
    }

    @Test
    @DisplayName("로그인 시각이 기록된다")
    void 로그인_시각_갱신() throws Exception {
        로그인();

        Integer count = jdbcClient.sql(
                        "SELECT COUNT(*) FROM app_user WHERE login_id = :id AND last_login_at IS NOT NULL")
                .param("id", LOGIN_ID)
                .query(Integer.class).single();

        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("리프레시 토큰은 원문이 아니라 해시로 저장된다")
    void 토큰_해시_저장() throws Exception {
        String refreshToken = 로그인().path("refreshToken").asText();

        Integer sameAsPlain = jdbcClient.sql(
                        "SELECT COUNT(*) FROM refresh_token WHERE token_hash = :token")
                .param("token", refreshToken)
                .query(Integer.class).single();
        Integer total = jdbcClient.sql("SELECT COUNT(*) FROM refresh_token")
                .query(Integer.class).single();

        assertThat(total).isEqualTo(1);       // 발급 기록은 남고
        assertThat(sameAsPlain).isZero();     // 원문 그대로는 저장되지 않는다
    }

    @Test
    @DisplayName("비밀번호가 틀리면 AUTH-201을 반환한다")
    void 비밀번호_불일치() throws Exception {
        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId":"%s","password":"WrongPa55!"}
                                """.formatted(LOGIN_ID)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH-201"));
    }

    @Test
    @DisplayName("없는 아이디도 비밀번호 불일치와 같은 에러를 반환한다")
    void 없는_아이디() throws Exception {
        // 에러를 구분하면 가입된 아이디가 노출된다
        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId":"nobody","password":"%s"}
                                """.formatted(PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH-201"));
    }

    @Test
    @DisplayName("탈퇴한 계정은 AUTH-202를 반환한다")
    void 탈퇴_계정() throws Exception {
        jdbcClient.sql("UPDATE app_user SET status = 'WITHDRAWN' WHERE login_id = :id")
                .param("id", LOGIN_ID)
                .update();

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId":"%s","password":"%s"}
                                """.formatted(LOGIN_ID, PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH-202"));
    }

    @Test
    @DisplayName("리프레시 토큰으로 새 토큰을 발급받는다")
    void 재발급_성공() throws Exception {
        String refreshToken = 로그인().path("refreshToken").asText();

        mockMvc.perform(post(REFRESH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(토큰_본문(refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty());
    }

    @Test
    @DisplayName("재발급에 쓴 토큰은 다시 쓸 수 없다")
    void 재사용_차단() throws Exception {
        String refreshToken = 로그인().path("refreshToken").asText();

        mockMvc.perform(post(REFRESH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(토큰_본문(refreshToken)))
                .andExpect(status().isOk());

        // 폐기된 토큰이 다시 들어오면 탈취를 의심할 수 있으므로 거부한다
        mockMvc.perform(post(REFRESH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(토큰_본문(refreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH-302"));
    }

    @Test
    @DisplayName("액세스 토큰으로는 재발급할 수 없다")
    void 액세스_토큰으로_재발급() throws Exception {
        // 두 토큰이 같은 키로 서명되므로 type 클레임을 확인하지 않으면 통과해 버린다
        String accessToken = 로그인().path("accessToken").asText();

        mockMvc.perform(post(REFRESH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(토큰_본문(accessToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH-302"));
    }

    @Test
    @DisplayName("위조된 토큰은 재발급을 거부한다")
    void 위조_토큰_재발급() throws Exception {
        mockMvc.perform(post(REFRESH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(토큰_본문("forged.token.value")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH-302"));
    }

    @Test
    @DisplayName("로그아웃하면 그 토큰으로 재발급할 수 없다")
    void 로그아웃() throws Exception {
        String refreshToken = 로그인().path("refreshToken").asText();

        mockMvc.perform(post(LOGOUT_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(토큰_본문(refreshToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post(REFRESH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(토큰_본문(refreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH-302"));
    }

    @Test
    @DisplayName("이미 폐기된 토큰으로 로그아웃해도 성공으로 처리한다")
    void 로그아웃_멱등() throws Exception {
        String refreshToken = 로그인().path("refreshToken").asText();

        mockMvc.perform(post(LOGOUT_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(토큰_본문(refreshToken)))
                .andExpect(status().isNoContent());

        // 목적("이 토큰을 못 쓰게 한다")은 이미 달성돼 있으므로 오류로 만들지 않는다
        mockMvc.perform(post(LOGOUT_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(토큰_본문(refreshToken)))
                .andExpect(status().isNoContent());
    }
}
