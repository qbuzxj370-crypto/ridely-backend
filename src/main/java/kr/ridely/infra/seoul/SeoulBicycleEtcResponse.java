package kr.ridely.infra.seoul;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 서울 열린데이터광장 "서울시 자전거 편의시설"(tvBicycleEtc) 응답 구조.
 *
 * 실제 응답:
 *   { "tvBicycleEtc": { "list_total_count": 3375,
 *                       "RESULT": { "CODE": "INFO-000", "MESSAGE": "정상 처리되었습니다" },
 *                       "row": [ ... ] } }
 *
 * ※ 이 클래스는 외부 API의 응답 형태일 뿐 우리 API 계약이 아니므로
 *   dto 패키지가 아닌 infra/seoul에 둔다.
 *
 * ※ 응답의 모든 값이 문자열로 온다(좌표 포함: "XCRD": "126.950519888").
 *   타입 변환은 사용하는 쪽에서 명시적으로 수행한다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SeoulBicycleEtcResponse implements SeoulApiResponse<SeoulBicycleEtcResponse.Row> {

    @JsonProperty("tvBicycleEtc")
    private Service service;

    /**
     * 인증키 오류 등 서비스 블록 없이 결과 코드만 내려오는 경우를 받는다.
     * 정상 응답에서는 null이다.
     */
    @JsonProperty("RESULT")
    private Result rootResult;

    /** 응답이 비정상 구조여도 NPE 없이 접근하기 위한 안전 메서드 */
    public List<Row> rows() {
        if (service == null || service.getRows() == null) {
            return Collections.emptyList();
        }
        return service.getRows();
    }

    /** 전체 결과 수 (페이지네이션 종료 판단용) */
    public int totalCount() {
        return service == null ? 0 : service.getListTotalCount();
    }

    /** 결과 코드 ("INFO-000" = 정상). 서비스 블록이 없으면 루트 결과를 본다 */
    public String resultCode() {
        if (service != null && service.getResult() != null) {
            return service.getResult().getCode();
        }
        return rootResult == null ? null : rootResult.getCode();
    }

    public String resultMessage() {
        if (service != null && service.getResult() != null) {
            return service.getResult().getMessage();
        }
        return rootResult == null ? null : rootResult.getMessage();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Service {

        @JsonProperty("list_total_count")
        private int listTotalCount;

        @JsonProperty("RESULT")
        private Result result;

        @JsonProperty("row")
        private List<Row> rows;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {

        @JsonProperty("CODE")
        private String code;

        @JsonProperty("MESSAGE")
        private String message;
    }

    /**
     * 편의시설 1건.
     *
     * ⚠️ 시설 종류(거치대·공기주입기·수리센터)를 구분하는 전용 필드가 없다.
     *   themeType·themeName은 전 건이 같은 값이고, 실제 구분 단서는
     *   facilityId·contentName·상세정보(DTL_INFO_*)에 흩어져 있다.
     *   분류 규칙은 전량 수집 후 분포를 보고 확정한다.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Row {

        /** 시설 ID. 자치구가 부여하는 값이라 형식이 제각각이다 (예: "은평구_공기주입기_49") */
        @JsonProperty("FCLT_ID")
        private String facilityId;

        /** 시설명 (예: " 자전거 일반거치대"). 앞뒤 공백이 섞여 있어 trim이 필요하다 */
        @JsonProperty("CONTS_NM")
        private String contentName;

        /** 테마 타입. 관측상 전 건 "일반"이라 분류에 쓸 수 없다 */
        @JsonProperty("THMS_TYPE")
        private String themeType;

        /** 테마명. 관측상 전 건 "(친환경)자전거 편의시설" */
        @JsonProperty("THMS_NM")
        private String themeName;

        /** 경도 (WGS84). 지오코딩 없이 그대로 쓸 수 있다 */
        @JsonProperty("XCRD")
        private String lng;

        /** 위도 (WGS84) */
        @JsonProperty("YCRD")
        private String lat;

        @JsonProperty("NEW_ADDR")
        private String newAddr;

        @JsonProperty("OLD_ADDR")
        private String oldAddr;

        /** "사용" / 그 외. 폐쇄된 시설을 거르는 기준 */
        @JsonProperty("USE_YN")
        private String useYn;

        @JsonProperty("MDFCN_YMD")
        private String modifiedAt;

        /**
         * 상세정보 DTL_INFO_NM01~NM10(항목명) / VL01~VL10(값).
         *
         * 필드를 20개 선언하는 대신 Map으로 모은다. 응답의 항목 순서가 일정하지 않고
         * (NM01, NM09, VL10, NM10, VL09 …) 자치구마다 채우는 항목이 달라
         * 개별 필드로 두면 의미 없는 빈 값만 늘어난다.
         */
        private final Map<String, String> details = new LinkedHashMap<>();

        @JsonAnySetter
        void collectUnmappedField(String name, Object value) {
            if (name != null && name.startsWith("DTL_INFO_")) {
                details.put(name, value == null ? null : value.toString());
            }
        }

        /** 상세정보 항목명 (번호 두 자리, 예: "06") */
        public String detailName(String no) {
            return details.get("DTL_INFO_NM" + no);
        }

        /** 상세정보 값 (번호 두 자리, 예: "06") */
        public String detailValue(String no) {
            return details.get("DTL_INFO_VL" + no);
        }
    }
}
