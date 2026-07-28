package kr.ridely.controller;

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
 * 회원가입 API 통합 테스트.
 * 컨트롤러 → 서비스 → DAO → 실제 PostgreSQL까지 전 구간을 검증한다.
 */
@AutoConfigureMockMvc
class AuthControllerTest extends AbstractIntegrationTest {

    private static final String SIGNUP_URL = "/api/v1/auth/signup";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbcClient;

    /**
     * 테스트 간 데이터 격리.
     * user_settings는 app_user를 FK로 참조하므로 CASCADE로 함께 삭제한다.
     */
    @BeforeEach
    void 데이터_초기화() {
        jdbcClient.sql("TRUNCATE TABLE app_user RESTART IDENTITY CASCADE").update();
    }

    private String body(String loginId, String password, String nickname) {
        return """
                {"loginId":"%s","password":"%s","nickname":"%s"}
                """.formatted(loginId, password, nickname);
    }

    @Test
    @DisplayName("정상 가입하면 201과 회원 정보를 반환한다")
    void 회원가입_성공() throws Exception {
        mockMvc.perform(post(SIGNUP_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("rider1", "Pa55word!", "한강라이더")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").isNumber())
                .andExpect(jsonPath("$.data.loginId").value("rider1"))
                .andExpect(jsonPath("$.data.nickname").value("한강라이더"))
                // DB가 채우는 값들이 응답에 담기는지 (재조회 경로 검증)
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.data.lastLoginAt").doesNotExist())
                // 비밀번호는 어떤 형태로도 응답에 포함되지 않아야 한다
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("비밀번호는 BCrypt 해시로 저장된다")
    void 비밀번호_해시_저장() throws Exception {
        mockMvc.perform(post(SIGNUP_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("rider2", "Pa55word!", "테스터")))
                .andExpect(status().isCreated());

        String hash = jdbcClient.sql("SELECT password_hash FROM app_user WHERE login_id = 'rider2'")
                .query(String.class)
                .single();

        assertThat(hash).startsWith("$2a$");     // BCrypt 형식
        assertThat(hash).isNotEqualTo("Pa55word!"); // 평문 저장 아님
    }

    @Test
    @DisplayName("가입 시 사용자 기본 설정 1행이 함께 생성된다")
    void 기본_설정_동시_생성() throws Exception {
        mockMvc.perform(post(SIGNUP_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("rider3", "Pa55word!", "테스터")))
                .andExpect(status().isCreated());

        Integer count = jdbcClient.sql("""
                        SELECT COUNT(*) FROM user_settings s
                          JOIN app_user u ON u.user_id = s.user_id
                         WHERE u.login_id = 'rider3'
                        """)
                .query(Integer.class)
                .single();

        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("이미 있는 아이디로 가입하면 AUTH-101을 반환한다")
    void 중복_아이디() throws Exception {
        mockMvc.perform(post(SIGNUP_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("rider4", "Pa55word!", "테스터")))
                .andExpect(status().isCreated());

        mockMvc.perform(post(SIGNUP_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("rider4", "Pa55word!", "다른사람")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("AUTH-101"));

        // 실패한 요청이 데이터를 남기지 않았는지
        Integer count = jdbcClient.sql("SELECT COUNT(*) FROM app_user WHERE login_id = 'rider4'")
                .query(Integer.class)
                .single();
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("정책에 못 미치는 비밀번호는 AUTH-102를 반환한다")
    void 약한_비밀번호() throws Exception {
        // 특수문자 없음 → 정책 위반. 형식 검증(COMMON-001)이 아니라 AUTH-102여야 한다
        mockMvc.perform(post(SIGNUP_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("rider5", "password1", "테스터")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("AUTH-102"));

        Integer count = jdbcClient.sql("SELECT COUNT(*) FROM app_user").query(Integer.class).single();
        assertThat(count).isZero();
    }

    @Test
    @DisplayName("필수값이 비면 COMMON-001과 필드별 메시지를 반환한다")
    void 입력값_검증_실패() throws Exception {
        mockMvc.perform(post(SIGNUP_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("rider6", "Pa55word!", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON-001"))
                .andExpect(jsonPath("$.error.details.nickname").isNotEmpty());
    }
}
