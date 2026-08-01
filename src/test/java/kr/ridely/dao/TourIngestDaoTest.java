package kr.ridely.dao;

import kr.ridely.infra.tourapi.TourApiListResponse;
import kr.ridely.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 관광 콘텐츠 적재 DAO 통합 테스트.
 *
 * 실제 PostGIS 컨테이너에 저장해 좌표가 제대로 들어가는지,
 * 재적재해도 중복이 생기지 않는지를 확인한다.
 */
class TourIngestDaoTest extends AbstractIntegrationTest {

    private static final double 선유도_경도 = 126.8997;
    private static final double 선유도_위도 = 37.5434;

    @Autowired
    private TourIngestDao tourIngestDao;

    @Autowired
    private JdbcClient jdbcClient;

    @BeforeEach
    void 데이터_초기화() {
        jdbcClient.sql("TRUNCATE TABLE tour_attraction RESTART IDENTITY CASCADE").update();
    }

    /** TourAPI 응답 1건을 흉내 낸 항목 */
    private TourApiListResponse.Item item(String contentId, String title) {
        TourApiListResponse.Item item = new TourApiListResponse.Item();
        item.setContentid(contentId);
        item.setContenttypeid("12");
        item.setTitle(title);
        item.setAddr1("서울특별시 영등포구 선유로 343");
        item.setAddr2("");
        item.setTel("");
        item.setFirstimage("http://tong.visitkorea.or.kr/cms/resource/sample_image2_1.jpg");
        item.setFirstimage2("http://tong.visitkorea.or.kr/cms/resource/sample_image3_1.jpg");
        item.setLDongRegnCd("11");
        return item;
    }

    private OffsetDateTime 수정일() {
        return OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.ofHours(9));
    }

    @Test
    @DisplayName("신규 콘텐츠를 저장하고 좌표를 원래 값으로 되읽는다")
    void 신규_저장() {
        tourIngestDao.upsert(item("127307", "선유도공원"), 선유도_경도, 선유도_위도, 1L, 수정일());

        assertThat(tourIngestDao.countAll()).isEqualTo(1);

        // geom에서 좌표를 다시 꺼내 저장 시 값과 일치하는지 확인 (경도·위도 순서 검증)
        Double lng = jdbcClient.sql("SELECT ST_X(geom) FROM tour_attraction WHERE content_id = '127307'")
                .query(Double.class).single();
        Double lat = jdbcClient.sql("SELECT ST_Y(geom) FROM tour_attraction WHERE content_id = '127307'")
                .query(Double.class).single();

        assertThat(lng).isCloseTo(선유도_경도, within(0.000001));
        assertThat(lat).isCloseTo(선유도_위도, within(0.000001));
    }

    @Test
    @DisplayName("좌표계(SRID)가 4326으로 저장된다")
    void 좌표계_확인() {
        tourIngestDao.upsert(item("127307", "선유도공원"), 선유도_경도, 선유도_위도, 1L, 수정일());

        Integer srid = jdbcClient.sql("SELECT ST_SRID(geom) FROM tour_attraction WHERE content_id = '127307'")
                .query(Integer.class).single();

        // SRID가 0이면 좌표계 미상이라 거리 계산(ST_DWithin)이 불가능하다
        assertThat(srid).isEqualTo(4326);
    }

    @Test
    @DisplayName("같은 콘텐츠를 다시 적재하면 행이 늘지 않고 값이 갱신된다")
    void 재적재_멱등성() {
        tourIngestDao.upsert(item("127307", "선유도공원"), 선유도_경도, 선유도_위도, 1L, 수정일());
        tourIngestDao.upsert(item("127307", "선유도공원(명칭 변경)"), 126.9, 37.55, 1L, 수정일());

        assertThat(tourIngestDao.countAll()).isEqualTo(1);

        String title = jdbcClient.sql("SELECT title FROM tour_attraction WHERE content_id = '127307'")
                .query(String.class).single();
        assertThat(title).isEqualTo("선유도공원(명칭 변경)");
    }

    @Test
    @DisplayName("공식 명세에서 삭제된 컬럼은 채우지 않는다")
    void 미사용_컬럼_확인() {
        tourIngestDao.upsert(item("127307", "선유도공원"), 선유도_경도, 선유도_위도, 1L, 수정일());

        Integer filled = jdbcClient.sql("""
                        SELECT COUNT(*) FROM tour_attraction
                         WHERE content_id = '127307'
                           AND (area_code IS NOT NULL OR sigungu_code IS NOT NULL
                                OR cat1 IS NOT NULL OR cat2 IS NOT NULL OR cat3 IS NOT NULL)
                        """)
                .query(Integer.class).single();

        assertThat(filled).isZero();
    }

    @Test
    @DisplayName("법정동 시도 코드로 지역을 찾는다")
    void 지역_조회() {
        // schema.sql의 region 시드: 11=서울, 28=인천, 41=경기
        assertThat(tourIngestDao.findRegionIdByCode("11")).isPresent();
        assertThat(tourIngestDao.findRegionIdByCode("99")).isEmpty();
    }

    @Test
    @DisplayName("지역을 찾지 못해도 저장할 수 있다")
    void 지역_없이_저장() {
        tourIngestDao.upsert(item("999999", "지역 미상 관광지"), 선유도_경도, 선유도_위도, null, 수정일());

        assertThat(tourIngestDao.countAll()).isEqualTo(1);
    }
}
