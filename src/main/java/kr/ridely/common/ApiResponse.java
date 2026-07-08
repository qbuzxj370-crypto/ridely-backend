package kr.ridely.common;

/**
 * 모든 API 응답을 감싸는 공통 envelope.
 * 형식: { success, data, error }
 *
 * 사용 예:
 *   성공: ApiResponse.ok(data)
 *   실패: ApiResponse.error(apiError)   // 보통 GlobalExceptionHandler가 생성
 *
 * @param <T> data 타입
 */
public record ApiResponse<T>(
        boolean success,
        T data,
        ApiError error
) {

    /** 성공 응답 (데이터 포함) */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    /** 성공 응답 (데이터 없음) */
    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(true, null, null);
    }

    /** 실패 응답 */
    public static <T> ApiResponse<T> error(ApiError error) {
        return new ApiResponse<>(false, null, error);
    }
}
