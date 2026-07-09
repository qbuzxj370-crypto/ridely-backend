package kr.ridely.common;

import lombok.*;
import lombok.Setter;

/**
 * 에러가 났을 때 응답에 담기는 정보.
 * ApiResponse 안의 error 자리에 들어간다.
 *
 * 예시:
 *   { "code": "AUTH-101", "message": "이미 가입된 계정입니다", "details": null }
 */
@Getter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class ApiError {

    /** 에러 코드 문자열 (예: "AUTH-101"). ErrorCode enum에서 가져온다 */
    private String code;

    /** 사용자에게 보여줄 메시지 */
    private String message;

    /**
     * 추가 상세 정보. 없으면 null.
     * 주로 입력값 검증 실패 시 어떤 필드가 왜 틀렸는지 담는다.
     * 예: { "password": "8자 이상이어야 합니다" }
     */
    private Object details;

    /** ErrorCode만으로 생성 (상세 정보 없음) */
    public static ApiError of(ErrorCode errorCode) {
        return new ApiError(errorCode.getCode(), errorCode.getMessage(), null);
    }

    /** ErrorCode + 상세 정보로 생성 */
    public static ApiError of(ErrorCode errorCode, Object details) {
        return new ApiError(errorCode.getCode(), errorCode.getMessage(), details);
    }
}