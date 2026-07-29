package kr.ridely.infra.tourapi;

import kr.ridely.common.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TourAPI 응답 파서 단위 테스트.
 * 샘플 JSON은 2026-07-28 선유도공원 좌표 실호출 응답에서 발췌·축약한 것이다.
 */
class TourApiResponseParserTest {

    private final TourApiResponseParser parser = new TourApiResponseParser();

    /** 정상 응답 (2건) */
    private static final String SAMPLE_OK = """
            {"response": {"header":{"resultCode":"0000","resultMsg":"OK"},
             "body": {"items": {"item":[
               {"addr1":"서울특별시 마포구 동교로8안길 23 (합정동)","addr2":"","zipcode":"04019",
                "areacode":"1","cat1":"A02","cat2":"A0201","cat3":"A02010700",
                "contentid":"127307","contenttypeid":"12","createdtime":"20020510090000",
                "dist":"859.2994255041023",
                "firstimage":"http://tong.visitkorea.or.kr/cms/resource/43/720543_image2_1.jpg",
                "firstimage2":"http://tong.visitkorea.or.kr/cms/resource/43/720543_image3_1.jpg",
                "cpyrhtDivCd":"Type3","mapx":"126.9047532101","mapy":"37.5499992917","mlevel":"6",
                "modifiedtime":"20251223131704","sigungucode":"13","tel":"","title":"망원정 터",
                "lDongRegnCd":"11","lDongSignguCd":"440",
                "lclsSystm1":"HS","lclsSystm2":"HS01","lclsSystm3":"HS011200"},
               {"addr1":"서울특별시 마포구 마포나루길 467","contentid":"1059638","contenttypeid":"12",
                "mapx":"126.8996000000","mapy":"37.5434000000","title":"선유도공원",
                "modifiedtime":"20250101000000","lDongRegnCd":"11","dist":"12.5"}
             ]}, "numOfRows":20, "pageNo":1, "totalCount":2}}}
            """;

    /** 결과 0건 — items가 객체가 아니라 빈 문자열로 온다 (TourAPI 특성) */
    private static final String SAMPLE_EMPTY = """
            {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},
             "body":{"items":"","numOfRows":20,"pageNo":1,"totalCount":0}}}
            """;

    /** 제공기관 오류 코드 */
    private static final String SAMPLE_ERROR = """
            {"response":{"header":{"resultCode":"22",
             "resultMsg":"LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR"},"body":{}}}
            """;

    @Test
    @DisplayName("정상 응답에서 목록과 전체 건수를 읽는다")
    void 정상_응답_파싱() {
        TourApiListResponse result = parser.parseList(SAMPLE_OK);

        assertThat(result.resultCode()).isEqualTo("0000");
        assertThat(result.totalCount()).isEqualTo(2);
        assertThat(result.items()).hasSize(2);
    }

    @Test
    @DisplayName("항목의 주요 필드가 매핑된다")
    void 항목_필드_매핑() {
        TourApiListResponse.Item item = parser.parseList(SAMPLE_OK).items().get(0);

        assertThat(item.getContentid()).isEqualTo("127307");
        assertThat(item.getContenttypeid()).isEqualTo("12");
        assertThat(item.getTitle()).isEqualTo("망원정 터");
        assertThat(item.getAddr1()).isEqualTo("서울특별시 마포구 동교로8안길 23 (합정동)");
        assertThat(item.getModifiedtime()).isEqualTo("20251223131704");
        // 법정동 시도 코드는 region.region_code와 값이 같다
        assertThat(item.getLDongRegnCd()).isEqualTo("11");
    }

    @Test
    @DisplayName("좌표는 문자열로 오며 mapx가 경도, mapy가 위도다")
    void 좌표_문자열_확인() {
        TourApiListResponse.Item item = parser.parseList(SAMPLE_OK).items().get(0);

        assertThat(item.getMapx()).isEqualTo("126.9047532101");
        assertThat(item.getMapy()).isEqualTo("37.5499992917");
        // 경도는 124~132, 위도는 33~39 범위 (한반도)
        assertThat(Double.parseDouble(item.getMapx())).isBetween(124.0, 132.0);
        assertThat(Double.parseDouble(item.getMapy())).isBetween(33.0, 39.0);
    }

    @Test
    @DisplayName("결과 0건이면 items가 빈 문자열로 와도 빈 목록으로 처리한다")
    void 결과_0건_처리() {
        TourApiListResponse result = parser.parseList(SAMPLE_EMPTY);

        assertThat(result.totalCount()).isZero();
        assertThat(result.items()).isEmpty();
    }

    @Test
    @DisplayName("선언하지 않은 응답 필드(areacode, cat1 등)가 있어도 파싱된다")
    void 미선언_필드_무시() {
        // SAMPLE_OK에는 areacode·cat1~3·lclsSystm1~3·cpyrhtDivCd 등이 포함돼 있다
        assertThat(parser.parseList(SAMPLE_OK).items()).isNotEmpty();
    }

    @Test
    @DisplayName("제공기관 오류 코드는 예외로 전환한다")
    void 오류_코드_예외() {
        assertThatThrownBy(() -> parser.parseList(SAMPLE_ERROR))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("JSON이 아닌 응답은 예외로 전환한다")
    void 파싱_실패_예외() {
        assertThatThrownBy(() -> parser.parseList("<OpenAPI_ServiceResponse><cmmMsgHeader/>"))
                .isInstanceOf(BusinessException.class);
    }
}
