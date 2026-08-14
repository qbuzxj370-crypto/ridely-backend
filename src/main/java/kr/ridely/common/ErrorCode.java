package kr.ridely.common;

import org.springframework.http.HttpStatus;

/**
 * 에러 코드 정의.
 *
 * 6개만 우선 정의. 개발 중 새 에러 케이스를 만나면 항목을 추가한다.
 * (ridely_api_spec.md의 22개 목록은 "예정 목록"으로만 참고)
 *
 * 추가 절차:
 *   1. 새 에러 케이스 발견
 *   2. 여기에 항목 추가 (PR 1개 = 코드 1~3개)
 *   3. PR 설명에 "왜 필요한지" 1줄
 */
public enum ErrorCode {

    // 공통
    COMMON_001("COMMON-001", HttpStatus.BAD_REQUEST,            "잘못된 요청입니다"),
    COMMON_002("COMMON-002", HttpStatus.UNAUTHORIZED,           "로그인이 필요합니다"),
    COMMON_004("COMMON-004", HttpStatus.NOT_FOUND,              "요청한 정보를 찾을 수 없습니다"),
    COMMON_500("COMMON-500", HttpStatus.INTERNAL_SERVER_ERROR,  "서버 오류가 발생했습니다"),

    // 인증
    AUTH_101("AUTH-101", HttpStatus.CONFLICT,     "이미 가입된 계정입니다"),
    AUTH_102("AUTH-102", HttpStatus.BAD_REQUEST,  "비밀번호 정책을 만족하지 않습니다"),
    AUTH_201("AUTH-201", HttpStatus.UNAUTHORIZED, "인증 정보가 올바르지 않습니다"),
    AUTH_202("AUTH-202", HttpStatus.UNAUTHORIZED, "사용할 수 없는 계정입니다"),
    AUTH_301("AUTH-301", HttpStatus.UNAUTHORIZED, "로그인이 만료되었습니다"),
    AUTH_302("AUTH-302", HttpStatus.UNAUTHORIZED, "다시 로그인해 주세요"),

    // POI
    POI_001("POI-001", HttpStatus.NOT_FOUND, "주변에 POI가 없습니다"),

    // 코스 추천
    // 004(추천 실패)·005(회피 라우팅 실패)는 fallback 체계와 함께 W4에서 추가한다.
    ROUTE_001("ROUTE-001", HttpStatus.BAD_REQUEST, "우선순위 합이 1이 되어야 합니다"),
    ROUTE_002("ROUTE-002", HttpStatus.BAD_REQUEST, "목표 거리가 적절하지 않습니다"),
    ROUTE_003("ROUTE-003", HttpStatus.BAD_REQUEST, "서비스 지역이 아닙니다");

    private final String code;
    private final HttpStatus httpStatus;
    private final String message;

    ErrorCode(String code, HttpStatus httpStatus, String message) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getMessage() {
        return message;
    }
}
