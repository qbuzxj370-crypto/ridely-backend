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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 내 정보 조회·수정 통합 테스트.
 *
 * 인증이 필요한 첫 API라, 토큰이 실제로 인증 정보로 이어지는지도 함께 검증한다.
 */
@AutoConfigureMockMvc
class UserControllerTest extends AbstractIntegrationTest {

    private static final String ME_URL = "/api/v1/users/me";
    private static final String LOGIN_ID = "rider";
    private static final String PASSWORD = "Pa55word!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private ObjectMapper objectMapper;

    private String accessToken;

    @BeforeEach
    void 회원가입_후_로그인() throws Exception {
        jdbcClient.sql("TRUNCATE TABLE app_user RESTART IDENTITY CASCADE").update();

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId":"%s","password":"%s","nickname":"라이더",
                                 "email":"rider@example.com"}
                                """.formatted(LOGIN_ID, PASSWORD)))
                .andExpect(status().isCreated());

        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId":"%s","password":"%s"}
                                """.formatted(LOGIN_ID, PASSWORD)))
                .andReturn().getResponse().getContentAsString();

        accessToken = objectMapper.readTree(body).path("data").path("accessToken").asText();
    }

    private String bearer() {
        return "Bearer " + accessToken;
    }

    @Test
    @DisplayName("토큰으로 내 정보를 조회한다")
    void 내정보_조회() throws Exception {
        mockMvc.perform(get(ME_URL).header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.loginId").value(LOGIN_ID))
                .andExpect(jsonPath("$.data.nickname").value("라이더"))
                .andExpect(jsonPath("$.data.email").value("rider@example.com"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                // 비밀번호는 어떤 형태로도 나가지 않아야 한다
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("보낸 항목만 수정하고 나머지는 유지한다")
    void 부분_수정() throws Exception {
        // 닉네임만 보냈는데 이메일이 null로 덮이면 안 된다.
        // 동적 SQL의 null 검사가 빠지면 이 테스트가 깨진다.
        mockMvc.perform(patch(ME_URL)
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname":"한강러너"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("한강러너"))
                .andExpect(jsonPath("$.data.email").value("rider@example.com"));
    }

    @Test
    @DisplayName("이메일만 수정할 수도 있다")
    void 이메일_수정() throws Exception {
        mockMvc.perform(patch(ME_URL)
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"new@example.com"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("new@example.com"))
                .andExpect(jsonPath("$.data.nickname").value("라이더"));
    }

    @Test
    @DisplayName("이메일 형식이 잘못되면 COMMON-001을 반환한다")
    void 이메일_형식_오류() throws Exception {
        mockMvc.perform(patch(ME_URL)
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"not-an-email"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON-001"))
                .andExpect(jsonPath("$.error.details.email").isNotEmpty());
    }

    @Test
    @DisplayName("토큰이 없으면 COMMON-002를 반환한다")
    void 무토큰() throws Exception {
        mockMvc.perform(get(ME_URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("COMMON-002"));
    }

    @Test
    @DisplayName("위조된 토큰은 COMMON-002를 반환한다")
    void 위조_토큰() throws Exception {
        mockMvc.perform(get(ME_URL).header(HttpHeaders.AUTHORIZATION, "Bearer forged.token.value"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("COMMON-002"));
    }

    @Test
    @DisplayName("리프레시 토큰으로는 API를 호출할 수 없다")
    void 리프레시_토큰으로_접근() throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"loginId":"%s","password":"%s"}
                                """.formatted(LOGIN_ID, PASSWORD)))
                .andReturn().getResponse().getContentAsString();
        String refreshToken = objectMapper.readTree(body).path("data").path("refreshToken").asText();

        // 수명이 긴 리프레시 토큰이 액세스 토큰처럼 쓰이면 안 된다
        mockMvc.perform(get(ME_URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + refreshToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("COMMON-002"));
    }
}
