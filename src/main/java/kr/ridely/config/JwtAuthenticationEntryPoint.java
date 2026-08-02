package kr.ridely.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import kr.ridely.common.ApiErrorWriter;
import kr.ridely.common.ErrorCode;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 인증 없이 보호된 경로에 접근했을 때의 응답 처리.
 *
 * 기본 동작은 Spring Security가 만든 오류 페이지를 내보내는 것이라
 * 우리 응답 형식({success, data, error})과 어긋난다.
 * 클라이언트가 모든 응답을 같은 형태로 파싱할 수 있도록 COMMON-002로 통일한다.
 */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ApiErrorWriter apiErrorWriter;

    public JwtAuthenticationEntryPoint(ApiErrorWriter apiErrorWriter) {
        this.apiErrorWriter = apiErrorWriter;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        apiErrorWriter.write(response, ErrorCode.COMMON_002);
    }
}
