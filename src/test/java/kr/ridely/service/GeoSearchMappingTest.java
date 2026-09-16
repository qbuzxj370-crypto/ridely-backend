package kr.ridely.service;

import kr.ridely.common.BusinessException;
import kr.ridely.common.ErrorCode;
import kr.ridely.config.MvpAreaProperties;
import kr.ridely.dto.geo.GeoPlaceDTO;
import kr.ridely.dto.geo.GeoSearchResponseDTO;
import kr.ridely.infra.kakao.KakaoKeywordResponse;
import kr.ridely.infra.kakao.KakaoLocalClient;
import kr.ridely.infra.kakao.KakaoProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 장소 검색 변환 단위 테스트.
 *
 * 외부 호출 없이 응답을 우리 DTO로 바꾸는 부분만 본다. {@link KakaoLocalClient}는 익명 서브클래스로 흉내 낸다.
 *
 * <b>겨냥하는 것은 셋이다.</b> 좌표 축이 뒤집히지 않는지, 서비스 지역 밖을 걸러내지 않고 표시만 하는지, 그리고 한 건이 깨져도 나머지가 살아남는지다.
 *
 * 실패를 {@code GEO-001}로 바꾸는 것은 클라이언트의 책임이라 여기서 보지 않는다. 그쪽은 WebClient를 세워야 확인되므로 서버 구동 후 확인으로 미룬다.
 */
class GeoSearchMappingTest {

    /** 여의도한강공원. x가 경도, y가 위도다 */
    private static final String IN_X = "126.9339";
    private static final String IN_Y = "37.5265";

    /** 해운대해수욕장. 서비스 지역 밖 */
    private static final String OUT_X = "129.1603";
    private static final String OUT_Y = "35.1587";

    @Test
    @DisplayName("x를 경도로 y를 위도로 읽는다")
    void mapsAxesCorrectly() {
        // ★ 뒤집히면 서울 좌표가 중국 어딘가가 된다. 그런데도 숫자는 그럴듯해서
        //   눈으로는 안 잡힌다
        GeoSearchResponseDTO response = service(doc("여의도한강공원", IN_X, IN_Y)).search("여의도");

        GeoPlaceDTO place = response.getItems().getFirst();
        assertThat(place.getLat()).isEqualTo(37.5265);
        assertThat(place.getLng()).isEqualTo(126.9339);
    }

    @Test
    @DisplayName("서비스 지역 밖도 결과에 남기고 표시만 한다")
    void keepsOutOfAreaWithFlag() {
        // 목록에서 빼면 사용자는 검색어를 잘못 썼다고 생각하고 계속 다시 친다
        GeoSearchResponseDTO response = service(
                doc("여의도한강공원", IN_X, IN_Y),
                doc("해운대해수욕장", OUT_X, OUT_Y)).search("공원");

        assertThat(response.getItems()).hasSize(2);
        assertThat(response.getItems())
                .extracting(GeoPlaceDTO::isInServiceArea)
                .containsExactly(true, false);
    }

    @Test
    @DisplayName("도로명 주소가 없으면 지번으로 내린다")
    void fallsBackToLotAddress() {
        // 공원·둔치는 건물이 없어 도로명이 비어 온다. 출발지로 쓰는 장소에 오히려 흔하다
        KakaoKeywordResponse.Document noRoad = new KakaoKeywordResponse.Document(
                "한강공원 난지지구", "", "서울 마포구 상암동 482", IN_X, IN_Y);

        GeoSearchResponseDTO response = service(noRoad).search("난지");

        assertThat(response.getItems().getFirst().getAddress())
                .isEqualTo("서울 마포구 상암동 482");
    }

    @Test
    @DisplayName("좌표가 깨진 건만 버리고 나머지는 살린다")
    void skipsOnlyBrokenRows() {
        // 한 건 때문에 검색 전체를 실패로 만들면 나머지 결과까지 못 쓰게 된다
        GeoSearchResponseDTO response = service(
                doc("정상", IN_X, IN_Y),
                doc("깨진 좌표", "", ""),
                doc("정상2", IN_X, IN_Y)).search("한강");

        assertThat(response.getItems()).hasSize(2);
        assertThat(response.getTotalCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("결과가 없으면 빈 배열이다")
    void emptyResultIsNotAnError() {
        // 오타나 없는 장소를 친 것은 실패가 아니다. GEO-001은 외부 호출이 실패했을 때만이다
        GeoSearchResponseDTO response = service().search("없는장소명입니다");

        assertThat(response.getItems()).isEmpty();
        assertThat(response.getTotalCount()).isZero();
        assertThat(response.getQuery()).isEqualTo("없는장소명입니다");
    }

    @Test
    @DisplayName("외부 실패는 그대로 올라간다")
    void externalFailurePropagates() {
        // 클라이언트가 이미 GEO-001로 바꿔 던진다. 서비스가 다시 감싸면 원인이 흐려진다
        GeoSearchServiceImpl failing = new GeoSearchServiceImpl(
                new KakaoLocalClient(WebClient.builder(), dummyProperties()) {
                    @Override
                    public List<KakaoKeywordResponse.Document> searchKeyword(String query, int size) {
                        throw new BusinessException(ErrorCode.GEO_001);
                    }
                }, mvpArea());

        assertThatThrownBy(() -> failing.search("여의도"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.GEO_001);
    }

    // ===== 도우미 =====

    private KakaoKeywordResponse.Document doc(String name, String x, String y) {
        return new KakaoKeywordResponse.Document(name, "서울 영등포구 여의동로 330", "서울 영등포구 여의도동", x, y);
    }

    /** application.yml의 ridely.mvp-area와 같은 값 */
    private MvpAreaProperties mvpArea() {
        return new MvpAreaProperties(37.500, 37.610, 126.780, 127.130);
    }

    /**
     * 생성자가 빌더로 WebClient를 만들기 때문에 null을 넣을 수 없다.
     * 실제 호출은 하지 않으므로 아무 주소나 둔다.
     */
    private KakaoProperties dummyProperties() {
        return new KakaoProperties("https://localhost", "test-key", "test-js-key", 5);
    }

    private GeoSearchServiceImpl service(KakaoKeywordResponse.Document... documents) {
        KakaoLocalClient client = new KakaoLocalClient(WebClient.builder(), dummyProperties()) {
            @Override
            public List<KakaoKeywordResponse.Document> searchKeyword(String query, int size) {
                return List.of(documents);
            }
        };
        return new GeoSearchServiceImpl(client, mvpArea());
    }
}
