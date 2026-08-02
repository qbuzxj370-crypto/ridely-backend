package kr.ridely.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 서블릿 응답에 ApiResponse 형식의 오류를 직접 쓰는 유틸.
 *
 * 필터에서 발생한 인증 실패는 컨트롤러에 도달하기 전이라
 * GlobalExceptionHandler(@RestControllerAdvice)가 잡지 못한다.
 * 그대로 두면 Spring Security 기본 오류 페이지가 나가 응답 형식이 깨지므로,
 * 여기서 같은 {success, data, error} 형태로 직접 써 준다.
 */
@Component
public class ApiErrorWriter {

    private final ObjectMapper objectMapper;

    public ApiErrorWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 지정한 에러코드로 응답 본문을 작성한다 */
    public void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getHttpStatus().value());
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        ApiResponse<Void> body = ApiResponse.error(ApiError.of(errorCode));
        objectMapper.writeValue(response.getWriter(), body);
    }
}
