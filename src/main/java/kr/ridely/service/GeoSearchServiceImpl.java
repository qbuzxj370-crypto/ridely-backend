package kr.ridely.service;

import kr.ridely.config.MvpAreaProperties;
import kr.ridely.dto.geo.GeoPlaceDTO;
import kr.ridely.dto.geo.GeoSearchResponseDTO;
import kr.ridely.infra.kakao.KakaoKeywordResponse;
import kr.ridely.infra.kakao.KakaoLocalClient;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 장소 검색 구현.
 *
 * <h3>서비스 지역 밖도 돌려준다</h3>
 *
 * 걸러내지 않고 {@code inServiceArea}로 표시만 한다. 「부산 해운대」를 쳤는데 결과가 0건이면 사용자는 검색이 고장났거나 이름을 잘못 쳤다고 여기고 계속 다시 친다. 찾아 주되 고를 수 없게 하는 편이 상황을 알린다.
 *
 * 추천 요청이 실제로 막히는 것은 {@code ROUTE-003}이고 판정 기준은 같은 {@link MvpAreaProperties}다. <b>두 곳이 어긋나면 검색에서는 선택 가능해 보였는데 추천에서 거부당한다.</b>
 */
@Service
@RequiredArgsConstructor
public class GeoSearchServiceImpl implements GeoSearchService {

    private static final Logger log = LoggerFactory.getLogger(GeoSearchServiceImpl.class);

    /**
     * 한 번에 돌려줄 최대 건수.
     *
     * 출발지를 고르는 목록이라 길 필요가 없다. 스크롤을 만들면 오히려 고르기 어려워지고, 검색어를 구체적으로 쓰게 하는 편이 빠르다.
     */
    private static final int MAX_RESULTS = 10;

    private final KakaoLocalClient kakaoLocalClient;
    private final MvpAreaProperties mvpArea;

    @Override
    public GeoSearchResponseDTO search(String query) {
        List<KakaoKeywordResponse.Document> documents =
                kakaoLocalClient.searchKeyword(query, MAX_RESULTS);

        List<GeoPlaceDTO> items = documents.stream()
                .map(this::toPlace)
                .filter(java.util.Objects::nonNull)
                .toList();

        return new GeoSearchResponseDTO(query, items, items.size());
    }

    /**
     * 외부 응답 한 건을 화면용으로 바꾼다.
     *
     * ⚠️ <b>{@code x}가 경도, {@code y}가 위도다.</b> 우리 코드는 (lat, lng) 순서라 반대다.
     *
     * 좌표를 못 읽으면 그 건만 버린다. 한 건 때문에 검색 전체를 실패로 만들면 나머지 결과까지 못 쓰게 된다.
     */
    private GeoPlaceDTO toPlace(KakaoKeywordResponse.Document doc) {
        try {
            double lng = Double.parseDouble(doc.x());
            double lat = Double.parseDouble(doc.y());
            return new GeoPlaceDTO(doc.placeName(), doc.displayAddress(),
                    lat, lng, mvpArea.contains(lng, lat));
        } catch (NumberFormatException | NullPointerException e) {
            log.warn("좌표를 읽지 못해 건너뛴다: place={}, x={}, y={}",
                    doc.placeName(), doc.x(), doc.y());
            return null;
        }
    }
}
