package kr.ridely.common;

import lombok.*;

/**
 * 모든 API 응답을 감싸는 공통 껍데기(envelope).
 *
 * 응답 형식이 항상 { success, data, error } 로 통일되므로
 * 프론트엔드는 success 값만 보고 성공/실패를 판단할 수 있다.
 *
 * 성공 예시:
 *   { "success": true, "data": { "userId": 1 }, "error": null }
 * 실패 예시:
 *   { "success": false, "data": null, "error": { "code": "AUTH-101", ... } }
 *
 * 사용법:
 *   성공  → return ApiResponse.ok(dto);
 *   실패  → GlobalExceptionHandler가 자동으로 만들어 준다 (직접 호출할 일 거의 없음)
 *
 * 정적 팩토리 메서드 패턴: 생성자가 아닌 내부 메서드 ok(), error()를 통해 객체를 생성하도록 강제
 *
 * @param <T> data 자리에 들어갈 실제 응답 타입 (예: SignupResponseDTO)
 */
@Getter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {

    /** 성공 여부. 성공이면 true, 실패면 false */
    private boolean success;

    /** 실제 응답 데이터. 실패 시에는 null */
    private T data;

    /** 에러 정보. 성공 시에는 null */
    private ApiError error;

    /** 성공 응답 (데이터 있음) */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    /** 성공 응답 (데이터 없음 — 예: 삭제 성공) */
    public static <T> ApiResponse<T> ok() {
        return new ApiResponse<>(true, null, null);
    }

    /** 실패 응답 */
    public static <T> ApiResponse<T> error(ApiError error) {
        return new ApiResponse<>(false, null, error);
    }
}