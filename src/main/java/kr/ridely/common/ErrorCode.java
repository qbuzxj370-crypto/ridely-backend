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
    COMMON_003("COMMON-003", HttpStatus.FORBIDDEN,              "접근 권한이 없습니다"),
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
    //
    // 인프라 POI 조회(/pois/nearby)는 이 코드를 쓰지 않는다. 0건이 정상 상태라서다 -
    // 서비스 지역 격자의 93.8%가 반경 1km에 수리소 0건이다(2026-09-15 측정).
    // 그쪽은 200에 빈 배열을 주고, 경계 밖만 ROUTE-003으로 가른다.
    POI_001("POI-001", HttpStatus.NOT_FOUND, "주변에 POI가 없습니다"),

    // 지오코딩
    //
    // 502인 이유는 우리 서버가 아니라 외부 서비스가 실패했기 때문이다. 500으로 내면
    // 클라이언트가 우리 장애와 구분하지 못하고, 재시도해도 되는 상황인지 판단할 수 없다.
    //
    // 검색 결과가 0건인 경우는 여기 해당하지 않는다. 오타나 없는 장소를 찾은 것이라
    // 실패가 아니고, 200에 빈 배열을 준다.
    GEO_001("GEO-001", HttpStatus.BAD_GATEWAY, "장소 검색 서비스에 연결하지 못했습니다"),

    // 코스 추천
    //
    // 004(LLM 최종 실패)는 넣지 않았다. 설계에는 있었으나 이 구조에서는 날 수 없다.
    // LLM이 실패하면 알고리즘 fallback으로 넘어가고, 그 fallback은 DB 후보만 쓴다.
    // 후보가 없는 경우는 아래 006이 이미 앞에서 걸러낸다. 즉 "최종 실패"라는 상태가 없다.
    //
    // 005(회피 라우팅 실패)도 넣지 않는다. 사고다발지는 교차로에 생기고 그 교차로가
    // 유일한 통로일 수 있어 회피 경로를 못 찾는 일이 있다. 그때는 코스를 안 주는 것보다
    // 회피를 포기하고 주는 편이 낫다고 보고, avoidDangerZonesApplied: false로 알린다.
    ROUTE_001("ROUTE-001", HttpStatus.BAD_REQUEST, "우선순위 합이 1이 되어야 합니다"),
    ROUTE_002("ROUTE-002", HttpStatus.BAD_REQUEST, "목표 거리가 적절하지 않습니다"),
    ROUTE_003("ROUTE-003", HttpStatus.BAD_REQUEST, "서비스 지역이 아닙니다"),
    // 003과 다르다. 003은 경계 밖이고 이쪽은 경계 안인데 적재된 데이터가 없는 경우다.
    // 003을 재사용하면 "여기는 서비스 지역이 아니다"라는 거짓말이 된다.
    //
    // 500이 아니라 422인 이유는 사용자가 고칠 수 있어서다. 출발지를 한강 쪽으로 옮기거나
    // 도착지를 지정하면 축이 선이 되어 그 주변에서 후보를 줍는다.
    // 서비스 지역을 450m 격자로 훑어 반경 1.5km 안의 후보를 세어 보면 6.4%가 0건이고
    // 거의 전부 경계선·모서리다. 한강이라는 띠를 사각형으로 덮어 모서리가 내륙으로 남았다.
    ROUTE_006("ROUTE-006", HttpStatus.UNPROCESSABLE_ENTITY,
            "주변에 코스를 만들 만한 장소가 없습니다. 출발지를 옮기거나 도착지를 지정해 보세요"),

    // 저장 경로
    // saved_route의 UNIQUE(user_id, recommended_route_id) 위반이다.
    // COMMON-001로 묶지 않은 이유는 화면이 "입력이 잘못됐다"와 "이미 저장했다"를 구분해야 해서다.
    // 앞은 입력을 고쳐야 하지만 뒤는 사용자가 원한 상태가 이미 이뤄진 것이라 안내 문구가 정반대다.
    SAVED_001("SAVED-001", HttpStatus.CONFLICT, "이미 저장한 코스입니다");

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
