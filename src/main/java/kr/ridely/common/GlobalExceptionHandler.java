package kr.ridely.common;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 전역 예외 처리.
 * 모든 컨트롤러에서 던진 예외를 잡아 ApiResponse.error 형식으로 변환한다.
 *
 * 처리 대상:
 *   - BusinessException        → 해당 ErrorCode의 HTTP 상태로 응답
 *   - 검증 실패(@Valid)         → COMMON-001
 *   - 그 외 모든 예외           → COMMON-500
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 비즈니스 예외 */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
        // TODO: 로깅 (WARN 레벨 - 예상된 예외)
        ErrorCode code = e.getErrorCode();
        log.warn("BusinessException {} - {}", code.getCode(), e.getMessage());
        ApiError error = ApiError.of(code, e.getDetails());
        return ResponseEntity.status(code.getHttpStatus())
                .body(ApiResponse.error(error));
    }

    /** Bean Validation 실패 (@Valid) */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        // TODO: e.getBindingResult()에서 필드별 오류 메시지 추출해 details로 전달
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(fe -> fieldErrors.putIfAbsent(fe.getField(), fe.getDefaultMessage()));
        log.warn("Validation 실패 - {}", fieldErrors);
        ApiError error = ApiError.of(ErrorCode.COMMON_001, fieldErrors);
        return ResponseEntity.status(ErrorCode.COMMON_001.getHttpStatus())
                .body(ApiResponse.error(error));
    }

    /** 그 외 모든 예외 (최후의 방어선) */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
        // TODO: 로깅 (ERROR 레벨 - 예상치 못한 예외, 스택트레이스 포함)
        log.error("예상치 못한 예외 발생", e);
        ApiError error = ApiError.of(ErrorCode.COMMON_500);
        return ResponseEntity.status(ErrorCode.COMMON_500.getHttpStatus())
                .body(ApiResponse.error(error));
    }
}
