package kr.ridely.dao;

import kr.ridely.dto.tour.TourAttractionDTO;
import kr.ridely.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 관광 콘텐츠 공간 조회 DAO 통합 테스트.
 *
 * 선유도공원을 중심으로 거리가 다른 지점 몇 개를 심어 두고
 * 반경 필터·거리순 정렬·타입 필터가 실제 PostGIS에서 동작하는지 확인한다.
 */
class TourSpatialDaoTest extends AbstractIntegrationTest {

    private static final double 중심_경도 = 126.8997;
    private static final double 중심_위도 = 37.5434;

    private static final List<String> 전체_타입 = List.of("12", "14", "39");

    @Autowired
    private TourSpatialDao tourSpatialDao;

    @Autowired
    private JdbcClient jdbcClient;

    @BeforeEach
    void 데이터_준비() {
        jdbcClient.sql("TRUNCATE TABLE tour_attraction RESTART IDENTITY CASCADE").update();

        // 중심에서 가까운 순: 선유도(0m) → 양화한강(약 1.2km) → 여의도(약 2.5km) → 반포(약 9km)
        insert("1001", "12", "선유도공원", 126.8997, 37.5434);
        insert("1002", "39", "양화한강공원 매점", 126.9100, 37.5450);
        insert("1003", "14", "여의도 문화시설", 126.9250, 37.5300);
        insert("1004", "12", "반포한강공원", 126.9954, 37.5100);
    }

    private void insert(String contentId, String typeId, String title, double lng, double lat) {
        jdbcClient.sql("""
                        INSERT INTO tour_attraction (content_id, content_type_id, title, geom)
                        VALUES (:contentId, :typeId, :title,
                                ST_SetSRID(ST_MakePoint(:lng, :lat), 4326))
                        """)
                .param("contentId", contentId)
                .param("typeId", typeId)
                .param("title", title)
                .param("lng", lng)
                .param("lat", lat)
                .update();
    }

    @Test
    @DisplayName("반경 안의 관광지를 가까운 순으로 반환한다")
    void 거리순_정렬() {
        List<TourAttractionDTO> result =
                tourSpatialDao.findNearby(중심_경도, 중심_위도, 5000, 전체_타입, 20);

        assertThat(result).extracting(TourAttractionDTO::getTitle)
                .containsExactly("선유도공원", "양화한강공원 매점", "여의도 문화시설");

        // 거리값도 오름차순이어야 한다
        assertThat(result).extracting(TourAttractionDTO::getDistanceM).isSorted();
    }

    @Test
    @DisplayName("반경 밖의 관광지는 제외한다")
    void 반경_필터() {
        // 반포는 약 9km 지점이라 5km 반경에서는 빠지고, 10km 반경에서는 들어온다
        List<TourAttractionDTO> 반경5km =
                tourSpatialDao.findNearby(중심_경도, 중심_위도, 5000, 전체_타입, 20);
        List<TourAttractionDTO> 반경10km =
                tourSpatialDao.findNearby(중심_경도, 중심_위도, 10000, 전체_타입, 20);

        assertThat(반경5km).extracting(TourAttractionDTO::getTitle)
                .doesNotContain("반포한강공원");
        assertThat(반경10km).extracting(TourAttractionDTO::getTitle)
                .contains("반포한강공원");
    }

    @Test
    @DisplayName("요청한 관광타입만 반환한다")
    void 타입_필터() {
        List<TourAttractionDTO> 관광지만 =
                tourSpatialDao.findNearby(중심_경도, 중심_위도, 10000, List.of("12"), 20);

        assertThat(관광지만).isNotEmpty();
        assertThat(관광지만).allMatch(dto -> "12".equals(dto.getContentTypeId()));
    }

    @Test
    @DisplayName("중심 좌표와 같은 지점은 거리가 0에 가깝다")
    void 거리_계산() {
        TourAttractionDTO 가장가까운 =
                tourSpatialDao.findNearby(중심_경도, 중심_위도, 5000, 전체_타입, 20).get(0);

        assertThat(가장가까운.getTitle()).isEqualTo("선유도공원");
        assertThat(가장가까운.getDistanceM()).isZero();
    }

    @Test
    @DisplayName("저장된 좌표를 그대로 되읽는다")
    void 좌표_반환() {
        TourAttractionDTO 선유도 =
                tourSpatialDao.findNearby(중심_경도, 중심_위도, 5000, 전체_타입, 20).get(0);

        // 위도·경도가 뒤바뀌지 않았는지 확인 (한반도 기준 위도 33~39, 경도 124~132)
        assertThat(선유도.getLat()).isBetween(33.0, 39.0);
        assertThat(선유도.getLng()).isBetween(124.0, 132.0);
    }

    @Test
    @DisplayName("limit만큼만 반환한다")
    void 건수_제한() {
        List<TourAttractionDTO> result =
                tourSpatialDao.findNearby(중심_경도, 중심_위도, 10000, 전체_타입, 2);

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("반경 내에 아무것도 없으면 빈 목록을 반환한다")
    void 결과_없음() {
        // 부산 앞바다 좌표
        List<TourAttractionDTO> result =
                tourSpatialDao.findNearby(129.0, 35.1, 1000, 전체_타입, 20);

        assertThat(result).isEmpty();
    }
}
