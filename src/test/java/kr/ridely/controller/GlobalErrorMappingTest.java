package kr.ridely.controller;

import kr.ridely.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 잘못 보낸 요청이 어떤 코드로 나가는지 고정한다.
 *
 * <b>{@code @ExceptionHandler(Exception.class)} 폴백이 Spring의 기본 404·405 처리를 가로챘고, 테스트가 없어 아무도 몰랐다.</b> 경로 오타 하나가 500으로 나가면 부른 쪽은 서버 장애로 읽고 백엔드에 문의한다. 연동 초기에 가장 흔한 실수라 왕복이 반복된다.
 *
 * 세 경로 모두 인증 없이 확인한다. 공개 경로를 쓰므로 토큰 준비가 필요 없고, 여기서 보려는 것도 인증이 아니다.
 */
@AutoConfigureMockMvc
class GlobalErrorMappingTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("없는 경로는 404 COMMON-004다")
    void 없는_경로() throws Exception {
        // /api/v1/pois/**는 공개라 인증은 통과하고 컨트롤러 매핑에서 걸린다.
        // 이 자리가 500이었다 - Exception 폴백이 Spring의 404 처리를 가로챘다
        mockMvc.perform(get("/api/v1/pois/no-such-path"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("COMMON-004"));
    }

    @Test
    @DisplayName("메서드가 다르면 405와 함께 허용 메서드를 알려준다")
    void 메서드_불일치() throws Exception {
        /*
         * ★ details와 Allow 헤더 양쪽을 본다. 예외가 허용 메서드를 들고 있는데
         *   버리면 부른 쪽이 문서를 다시 뒤져야 한다. Allow는 500으로 나가던 때 사라졌던 헤더다.
         */
        mockMvc.perform(get("/api/v1/auth/login"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error.code").value("COMMON-006"))
                .andExpect(jsonPath("$.error.details.allowed").value("POST"))
                .andExpect(header().string(HttpHeaders.ALLOW, org.hamcrest.Matchers.containsString("POST")));
    }

    @Test
    @DisplayName("Content-Type이 다르면 415와 함께 받는 형식을 알려준다")
    void 콘텐츠_타입_불일치() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("loginId=rider"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error.code").value("COMMON-007"))
                .andExpect(jsonPath("$.error.details.supported")
                        .value(org.hamcrest.Matchers.containsString("application/json")));
    }
}
