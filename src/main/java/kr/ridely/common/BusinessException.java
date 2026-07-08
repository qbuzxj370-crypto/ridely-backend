package kr.ridely.common;

/**
 * 비즈니스 로직 예외.
 * ErrorCode를 담아서 던지면 GlobalExceptionHandler가 잡아 ApiResponse로 변환한다.
 *
 * 사용 예:
 *   if (authDao.existsByLoginId(loginId) > 0) {
 *       throw new BusinessException(ErrorCode.AUTH_101);
 *   }
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final transient Object details;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.details = null;
    }

    public BusinessException(ErrorCode errorCode, Object details) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.details = details;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public Object getDetails() {
        return details;
    }
}
