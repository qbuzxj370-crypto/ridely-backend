package kr.ridely.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 전역 예외 처리.
 * 모든 컨트롤러에서 던진 예외를 잡아 ApiResponse.error 형식으로 변환한다.
 *
 * 처리 대상:
 *   - BusinessException            → 해당 ErrorCode의 HTTP 상태로 응답
 *   - 요청 본문 검증 실패(@Valid)   → COMMON-001
 *   - 요청 본문을 읽지 못함(JSON 문법 오류·필수 본문 누락) → COMMON-001
 *   - 쿼리 파라미터 검증 실패       → COMMON-001
 *   - 없는 경로                    → COMMON-004
 *   - 메서드 불일치                 → COMMON-006 (+ Allow 헤더)
 *   - Content-Type 불일치           → COMMON-007
 *   - 그 외 모든 예외              → COMMON-500
 *
 * <b>Exception 폴백이 Spring의 기본 처리를 가로챈다.</b> 경로 오타·메서드 불일치는 원래
 * Spring이 404·405로 처리하는데, 이 클래스가 생기면서 전부 500으로 나가게 됐다.
 * 클라이언트 잘못을 서버 장애로 보고하는 셈이라 개별 핸들러로 되돌려 놓았다.
 *
 * ⚠️ 새 폴백을 추가할 때 같은 일이 반복된다. 400대로 나가야 할 표준 예외를 삼키지 않는지
 * 확인한다. 이 규칙은 테스트(GlobalErrorMappingTest)로 고정돼 있다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 비즈니스 예외 (예상된 예외) */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
        ErrorCode code = e.getErrorCode();
        log.warn("BusinessException {} - {}", code.getCode(), e.getMessage());
        ApiError error = ApiError.of(code, e.getDetails());
        return ResponseEntity.status(code.getHttpStatus())
                .body(ApiResponse.error(error));
    }

    /** 요청 본문 검증 실패 (@Valid @RequestBody) */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(fe -> fieldErrors.putIfAbsent(fe.getField(), fe.getDefaultMessage()));
        log.warn("Validation 실패 - {}", fieldErrors);
        return badRequest(fieldErrors);
    }

    /**
     * 쿼리 파라미터 검증 실패 (@Validated + @RequestParam 제약).
     *
     * 요청 본문 검증과 달리 ConstraintViolationException으로 올라오기 때문에
     * 따로 잡지 않으면 COMMON-500이 되어 버린다.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException e) {
        Map<String, String> violations = new LinkedHashMap<>();
        e.getConstraintViolations().forEach(v -> {
            // propertyPath가 "메서드명.파라미터명" 형태라 마지막 조각만 쓴다
            String path = v.getPropertyPath().toString();
            String field = path.substring(path.lastIndexOf('.') + 1);
            violations.putIfAbsent(field, v.getMessage());
        });
        log.warn("파라미터 검증 실패 - {}", violations);
        return badRequest(violations);
    }

    /** 필수 쿼리 파라미터 누락 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParam(MissingServletRequestParameterException e) {
        log.warn("필수 파라미터 누락 - {}", e.getParameterName());
        return badRequest(Map.of(e.getParameterName(), "필수 파라미터입니다"));
    }

    /** 파라미터 타입 불일치 (예: lat=abc) */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("파라미터 타입 불일치 - {}={}", e.getName(), e.getValue());
        return badRequest(Map.of(e.getName(), "값의 형식이 올바르지 않습니다"));
    }

    /**
     * 요청 본문을 읽지 못함 (JSON 문법 오류, 타입 불일치, 필수 본문 누락).
     *
     * 이 핸들러가 없으면 Exception 폴백으로 떨어져 COMMON-500이 나간다. 따옴표 하나 빠뜨린 요청이
     * 서버 장애로 보이고, 클라이언트는 재시도해야 할지 요청을 고쳐야 할지 판단할 수 없다.
     *
     * 파싱 위치·기대 타입은 details에 넣지 않는다. 내부 클래스명과 필드 경로가 그대로 노출된다.
     * 개발자는 서버 로그를 본다.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(HttpMessageNotReadableException e) {
        log.warn("요청 본문을 읽지 못했다 - {}", e.getMessage());
        return badRequest(Map.of("body", "요청 본문의 형식이 올바르지 않습니다"));
    }

    /**
     * 없는 경로.
     *
     * <b>아래 Exception 폴백이 Spring의 기본 404 처리를 가로챈 자리다.</b> 폴백이 생기면서
     * 경로 오타가 500으로 나가게 됐고, 클라이언트는 서버 장애로 읽는다.
     *
     * 경로는 부른 쪽이 이미 알고 있어 details에 넣지 않는다. 대신 로그에 남긴다 -
     * 어느 경로였는지 없으면 나중에 원인을 찾을 수 없다.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException e,
                                                              HttpServletRequest request) {
        log.warn("없는 경로 - {} {}", request.getMethod(), request.getRequestURI());
        return of(ErrorCode.COMMON_004, null);
    }

    /**
     * 경로는 맞는데 메서드가 다르다.
     *
     * <b>예외가 허용 메서드를 들고 있다.</b> 버리면 부른 쪽이 문서를 다시 뒤져야 하므로
     * details와 Allow 헤더 양쪽에 담는다. Allow는 Spring 기본 처리가 붙이던 것이다.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException e, HttpServletRequest request) {

        Set<HttpMethod> allowed = e.getSupportedHttpMethods();
        String allowedText = allowed == null ? "" :
                allowed.stream().map(HttpMethod::name).sorted().collect(Collectors.joining(", "));

        log.warn("지원하지 않는 메서드 - {} {} (허용: {})",
                request.getMethod(), request.getRequestURI(), allowedText);

        ApiError error = ApiError.of(ErrorCode.COMMON_006, Map.of("allowed", allowedText));
        ResponseEntity.BodyBuilder response =
                ResponseEntity.status(ErrorCode.COMMON_006.getHttpStatus());
        if (allowed != null && !allowed.isEmpty()) {
            response.allow(allowed.toArray(new HttpMethod[0]));
        }
        return response.body(ApiResponse.error(error));
    }

    /**
     * Content-Type이 없거나 다르다.
     *
     * 본문을 보내면서 헤더를 빠뜨리면 서블릿이 기본값을 붙여 여기로 온다. 받는 타입을
     * details에 담는다 - 예외가 이미 가지고 있는 값이다.
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException e, HttpServletRequest request) {

        String supported = e.getSupportedMediaTypes().stream()
                .map(MediaType::toString).collect(Collectors.joining(", "));

        log.warn("지원하지 않는 Content-Type - {} {} (받은 값: {})",
                request.getMethod(), request.getRequestURI(), e.getContentType());

        return of(ErrorCode.COMMON_007, Map.of("supported", supported));
    }

    /** 그 외 모든 예외 (최후의 방어선) — 스택트레이스 포함 ERROR 로깅 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
        log.error("예상치 못한 예외 발생", e);
        ApiError error = ApiError.of(ErrorCode.COMMON_500);
        return ResponseEntity.status(ErrorCode.COMMON_500.getHttpStatus())
                .body(ApiResponse.error(error));
    }

    /** COMMON-001 응답 조립 */
    private ResponseEntity<ApiResponse<Void>> badRequest(Map<String, String> details) {
        return of(ErrorCode.COMMON_001, details);
    }

    /** 상태 코드와 details를 지정해 응답을 만든다 */
    private ResponseEntity<ApiResponse<Void>> of(ErrorCode code, Map<String, String> details) {
        ApiError error = details == null ? ApiError.of(code) : ApiError.of(code, details);
        return ResponseEntity.status(code.getHttpStatus()).body(ApiResponse.error(error));
    }
}
