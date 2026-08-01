package kr.ridely.infra.tourapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Collections;
import java.util.List;

/**
 * TourAPI 목록 오퍼레이션(locationBasedList2 등) 응답 구조.
 *
 * 실제 응답이 4단계로 중첩되어 있으므로 그대로 옮긴다.
 *   response → body → items → item[]
 *
 * ※ 이 클래스는 외부 API의 응답 형태일 뿐 우리 API 계약이 아니므로
 *   dto 패키지가 아닌 infra/tourapi에 둔다. 우리 응답에는 TourAttractionDTO를 사용한다.
 *
 * ※ 응답의 모든 값이 문자열로 온다 (숫자·좌표 포함: "mapx": "126.9047532101").
 *   타입 변환은 이 클래스가 아니라 사용하는 쪽에서 명시적으로 수행한다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TourApiListResponse {

    private Response response;

    /** 응답이 비정상 구조일 때를 대비한 안전 접근 */
    public List<Item> items() {
        if (response == null || response.getBody() == null
                || response.getBody().getItems() == null
                || response.getBody().getItems().getItem() == null) {
            return Collections.emptyList();
        }
        return response.getBody().getItems().getItem();
    }

    /** 전체 결과 수 (페이지네이션 종료 판단용) */
    public int totalCount() {
        if (response == null || response.getBody() == null) {
            return 0;
        }
        return response.getBody().getTotalCount();
    }

    /** 제공기관 결과 코드 ("0000" = 정상) */
    public String resultCode() {
        return (response == null || response.getHeader() == null)
                ? null : response.getHeader().getResultCode();
    }

    public String resultMsg() {
        return (response == null || response.getHeader() == null)
                ? null : response.getHeader().getResultMsg();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Response {
        private Header header;
        private Body body;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Header {
        private String resultCode;
        private String resultMsg;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Body {
        /**
         * 결과가 0건이면 이 필드가 객체가 아니라 빈 문자열("")로 온다.
         * TourApiResponseParser의 ACCEPT_EMPTY_STRING_AS_NULL_OBJECT 설정으로 null 처리된다.
         */
        private Items items;
        private int numOfRows;
        private int pageNo;
        private int totalCount;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Items {
        private List<Item> item;
    }

    /**
     * 관광 콘텐츠 1건.
     *
     * 필드는 우리가 실제로 쓰는 것만 선언한다.
     * 공식 명세에서 삭제된 areacode·sigungucode·cat1~3은 응답에 여전히 오지만
     * 하위 호환용 잔존 값이라 사용하지 않는다.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {

        /** 콘텐츠 ID. 재적재 시 멱등성 기준 (UNIQUE) */
        private String contentid;

        /** 관광타입 (12=관광지, 14=문화시설, 39=음식점 등) */
        private String contenttypeid;

        private String title;
        private String addr1;
        private String addr2;
        private String zipcode;
        private String tel;

        /** GPS X좌표 = 경도. 문자열로 온다 */
        private String mapx;

        /** GPS Y좌표 = 위도. 문자열로 온다 */
        private String mapy;

        /** 대표이미지 원본 (약 500×333) */
        private String firstimage;

        /** 대표이미지 썸네일 (약 150×100) */
        private String firstimage2;

        /** 콘텐츠 수정일 (yyyyMMddHHmmss). 증분 동기화 기준 */
        private String modifiedtime;

        /**
         * 법정동 시도 코드. region.region_code와 값이 일치한다 ("11"=서울).
         *
         * @JsonProperty를 명시하는 이유: 필드명 두 번째 글자가 대문자라 Jackson이 프로퍼티명을 "LDongRegnCd"로 인식해 매핑에 실패한다.
         */
        @JsonProperty("lDongRegnCd")
        private String lDongRegnCd;

        /** 중심 좌표로부터 거리(m). 조회 시점 값이라 저장하지 않는다 */
        private String dist;
    }
}
