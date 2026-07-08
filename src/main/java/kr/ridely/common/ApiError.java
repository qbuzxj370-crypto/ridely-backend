package kr.ridely.common;

/**
 * 에러 응답 본문.
 * ApiResponse.error 안에 담긴다.
 *
 * @param code    에러 코드 (예: "AUTH-101")
 * @param message 사용자에게 보여줄 메시지
 * @param details 추가 상세 (필드별 검증 오류 등, 없으면 null)
 */
public record ApiError(
        String code,
        String message,
        Object details
) {

    /** ErrorCode로부터 생성 (details 없음) */
    public static ApiError of(ErrorCode errorCode) {
        return new ApiError(errorCode.getCode(), errorCode.getMessage(), null);
    }

    /** ErrorCode + 상세 정보로 생성 */
    public static ApiError of(ErrorCode errorCode, Object details) {
        return new ApiError(errorCode.getCode(), errorCode.getMessage(), details);
    }
}
