package kr.ridely.infra.taas;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Collections;
import java.util.List;

/**
 * TAAS 자전거사고 다발지역 REST 조회 응답 (JSON).
 *
 * ⚠️ 명세와 실제가 다르다.
 *
 * 활용가이드 v1.1은 XML 예제만 싣는다. 거기서는 response 밑에 header와 body가 있고
 * 그 안에 resultCode "0000"이 들어간다. type=json으로 부르면 구조가 이렇게 바뀐다.
 *
 *   { "resultCode":"00", "resultMsg":"NORMAL_CODE",
 *     "items":{"item":[ ... ]}, "totalCount":1, "numOfRows":10, "pageNo":1 }
 *
 * header/body 래핑이 없고 정상 코드도 두 자리다.
 * JSON 구조는 문서화돼 있지 않아 이 클래스는 실호출로 확인한 형태를 따른다.
 *
 * ※ 외부 API의 응답 형태일 뿐 우리 API 계약이 아니므로 dto가 아닌 infra/taas에 둔다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TaasFrequentZoneResponse {

    /** ⚠️ 정상은 "00"이다. 명세(XML)의 "0000"이 아니다 */
    @JsonProperty("resultCode")
    private String resultCode;

    @JsonProperty("resultMsg")
    private String resultMsg;

    @JsonProperty("items")
    private Items items;

    @JsonProperty("totalCount")
    private Integer totalCount;

    /** 응답이 비정상 구조여도 NPE 없이 접근하기 위한 안전 메서드 */
    public List<Item> itemList() {
        if (items == null || items.getItem() == null) {
            return Collections.emptyList();
        }
        return items.getItem();
    }

    public int totalCountOrZero() {
        return totalCount == null ? 0 : totalCount;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Items {

        @JsonProperty("item")
        private List<Item> item;
    }

    /** 다발지역 1건 */
    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {

        /** 다발지역FID. 공간정보 식별자. JSON에서 숫자로 온다 */
        @JsonProperty("afos_fid")
        private Long afosFid;

        /**
         * 다발지역ID. 연도 데이터셋 식별자다.
         * 활용가이드: "2021년 다발지역의 afos_id는 모두 2022083".
         * searchYearCd 대신 이 값을 넣어도 결과가 같다 — 3.1 코드표 참조.
         */
        @JsonProperty("afos_id")
        private String afosId;

        /** 법정동코드 10자리. 앞 2자리가 시도코드라 region 매핑에 쓴다 */
        @JsonProperty("bjd_cd")
        private String bjdCd;

        @JsonProperty("spot_cd")
        private String spotCd;

        /**
         * 시도시군구명. 일련번호가 붙는다("서울 강남구1").
         * ⚠️ 연도별로 표기가 다르다 — 2023은 "서울 강남구1", 2022는 "서울특별시 강남구1".
         * 그래서 지역 매핑에는 이 값이 아니라 bjd_cd를 쓴다.
         */
        @JsonProperty("sido_sgg_nm")
        private String sidoSggNm;

        /** 지점명. 표시용으로는 이쪽을 쓴다 */
        @JsonProperty("spot_nm")
        private String spotNm;

        /** 사고건수 */
        @JsonProperty("occrrnc_cnt")
        private Integer occrrncCnt;

        /** 사상자수 */
        @JsonProperty("caslt_cnt")
        private Integer casltCnt;

        /** 사망자수 */
        @JsonProperty("dth_dnv_cnt")
        private Integer dthDnvCnt;

        /** 중상자수 (se = severe) */
        @JsonProperty("se_dnv_cnt")
        private Integer seDnvCnt;

        /** 경상자수 (sl = slight). ⚠️ se와 헷갈리기 쉽다 */
        @JsonProperty("sl_dnv_cnt")
        private Integer slDnvCnt;

        /** 부상신고자수 */
        @JsonProperty("wnd_dnv_cnt")
        private Integer wndDnvCnt;

        /**
         * 다발지역 폴리곤 GeoJSON 문자열 (EPSG 4326).
         *
         * ⚠️ 명세는 {@code Polygon}만 예시하지만 실제로 {@code MultiPolygon}이 오는 연도가 있다.
         * 적재 시 {@code ST_Multi()}로 감싸 통일한다 — V3 마이그레이션 참조.
         */
        @JsonProperty("geom_json")
        private String geomJson;

        /** 중심점 경도. 문자열로 온다 */
        @JsonProperty("lo_crd")
        private String loCrd;

        /** 중심점 위도 */
        @JsonProperty("la_crd")
        private String laCrd;
    }
}
