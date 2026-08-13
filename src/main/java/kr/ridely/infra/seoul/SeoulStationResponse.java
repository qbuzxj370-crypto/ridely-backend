package kr.ridely.infra.seoul;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Collections;
import java.util.List;

/**
 * 서울 열린데이터광장 "서울시 공공자전거 대여소 정보"(tbCycleStationInfo) 응답 구조.
 *
 * <p>⚠️ <b>JSON 루트 키가 서비스명과 다르다.</b> 서비스명은 {@code tbCycleStationInfo}인데
 * 응답 루트는 {@code stationInfo}다. 서비스명으로 찾으면 전 건 파싱에 실패한다
 * — docs/shared/DATA_SOURCES.md 2.4
 *
 * <pre>
 *   { "stationInfo": { "list_total_count": 3237,
 *                      "RESULT": { "CODE": "INFO-000", "MESSAGE": "정상 처리되었습니다" },
 *                      "row": [ ... ] } }
 * </pre>
 *
 * <p>※ 이 클래스는 외부 API의 응답 형태일 뿐 우리 API 계약이 아니므로
 *   dto 패키지가 아닌 infra/seoul에 둔다.
 *
 * <p>※ 좌표·거치대 수를 포함해 모든 값이 문자열로 온다. 타입 변환은 사용하는 쪽에서 한다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SeoulStationResponse implements SeoulApiResponse<SeoulStationResponse.Row> {

    /** ⚠️ 서비스명(tbCycleStationInfo)이 아니라 stationInfo다 */
    @JsonProperty("stationInfo")
    private Service service;

    /** 인증키 오류 등 서비스 블록 없이 결과 코드만 내려오는 경우를 받는다 */
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

    /** 대여소 1건 */
    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Row {

        /** 대여소 ID (예: "ST-10"). bike_station.station_code UNIQUE로 쓴다 */
        @JsonProperty("RENT_ID")
        private String rentId;

        /** 대여소명 (예: "108. 서교동 사거리") */
        @JsonProperty("RENT_NM")
        private String rentName;

        /** 경도 (WGS84). 문자열로 온다 */
        @JsonProperty("STA_LONG")
        private String lng;

        /** 위도 (WGS84) */
        @JsonProperty("STA_LAT")
        private String lat;

        /** 거치대 수 */
        @JsonProperty("HOLD_NUM")
        private String holdNum;

        /** 자치구 (예: "마포구"). region 시드가 시도 단위뿐이라 현재는 참고용이다 */
        @JsonProperty("STA_LOC")
        private String district;

        /** 주소 */
        @JsonProperty("STA_ADD1")
        private String addr1;

        @JsonProperty("STA_ADD2")
        private String addr2;
    }
}
