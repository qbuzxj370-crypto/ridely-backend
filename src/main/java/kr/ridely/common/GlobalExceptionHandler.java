package kr.ridely.common;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 전역 예외 처리.
 * 모든 컨트롤러에서 던진 예외를 잡아 ApiResponse.error 형식으로 변환한다.
 *
 * 처리 대상:
 *   - BusinessException            → 해당 ErrorCode의 HTTP 상태로 응답
 *   - 요청 본문 검증 실패(@Valid)   → COMMON-001
 *   - 요청 본문을 읽지 못함(JSON 문법 오류·필수 본문 누락) → COMMON-001
 *   - 쿼리 파라미터 검증 실패       → COMMON-001
 *   - 그 외 모든 예외              → COMMON-500
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
        ApiError error = ApiError.of(ErrorCode.COMMON_001, details);
        return ResponseEntity.status(ErrorCode.COMMON_001.getHttpStatus())
                .body(ApiResponse.error(error));
    }
}
