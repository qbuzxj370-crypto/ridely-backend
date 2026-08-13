package kr.ridely.service;

import kr.ridely.dto.poi.RouteFacilityIngestResultDTO;
import kr.ridely.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 자전거길 주변시설 적재 통합 테스트.
 *
 * 파싱 자체는 {@code RouteFacilityCsvReaderTest}가 맡는다.
 * 여기서는 영속·좌표 기반 노선 연결·멱등성만 본다.
 */
class RouteFacilityIngestTest extends AbstractIntegrationTest {

    /** 노선 픽스처. 시설을 붙일 대상이 필요해 함께 적재한다 */
    private static final String ROUTES_CSV = """
            순서,국토종주 자전거길,위도(LINE_XP),경도(LINE_YP)
            1,1,37.5573,126.6034
            2,1,37.5740,126.6070
            3,1,37.5900,126.6100
            1,2,37.5434,126.8997
            2,2,37.5350,126.9200
            3,2,37.5265,126.9339
            """;

    /**
     * 시설 픽스처. 3열이 경도, 4열이 위도다.
     *
     * - 인증센터 : 아라 노선에서 약 90m → 연결돼야 한다
     * - 화장실   : 한강종주 노선에서 약 30m → 연결돼야 한다
     * - 급수대   : 부산 앞바다. 어느 노선에서도 멀다 → NULL로 남아야 한다
     * - 마지막 행 : 화장실과 완전 중복 → 제거돼야 한다
     */
    private static final String FACILITIES_CSV = """
            구분,이름,경도,위도
            인증센터,아라서해갑문인증센터,126.6040,37.5580
            화장실,한강종주길,126.9000,37.5435
            급수대,멀리있는급수대,129.0000,35.0000
            화장실,한강종주길,126.9000,37.5435
            """;

    private static final Path ROUTES_PATH;
    private static final Path FACILITIES_PATH;

    static {
        try {
            Path directory = Files.createTempDirectory("ridely-facility-test");
            ROUTES_PATH = directory.resolve("bike-route-coords.csv");
            FACILITIES_PATH = directory.resolve("route-facilities.csv");
            // 원본 배포본과 같은 CP949로 쓴다
            Files.write(ROUTES_PATH, ROUTES_CSV.getBytes(Charset.forName("MS949")));
            Files.write(FACILITIES_PATH, FACILITIES_CSV.getBytes(Charset.forName("MS949")));
            ROUTES_PATH.toFile().deleteOnExit();
            FACILITIES_PATH.toFile().deleteOnExit();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void seedFilePaths(DynamicPropertyRegistry registry) {
        registry.add("ridely.seed.bike-route-coords", ROUTES_PATH::toString);
        registry.add("ridely.seed.bike-route-facilities", FACILITIES_PATH::toString);
    }

    @Autowired
    private BikeRouteIngestService bikeRouteIngestService;

    @Autowired
    private RouteFacilityIngestService routeFacilityIngestService;

    @Autowired
    private JdbcClient jdbcClient;

    /** FK 순서대로 비운다 */
    @BeforeEach
    void clearTables() {
        jdbcClient.sql("DELETE FROM route_facility").update();
        jdbcClient.sql("DELETE FROM national_bike_route").update();
    }

    @Test
    @DisplayName("중복을 뺀 시설을 적재하고 종류별로 집계한다")
    void 시설_적재() {
        RouteFacilityIngestResultDTO result = routeFacilityIngestService.ingestRouteFacilities();

        assertThat(result.getDataRowCount()).isEqualTo(4);
        assertThat(result.getDuplicateRowCount()).isEqualTo(1);
        assertThat(result.getUnknownLabelCount()).isZero();
        assertThat(result.getInsertedCount()).isEqualTo(3);
        assertThat(result.getCharsetName()).contains("949");

        assertThat(result.getCountByType())
                .containsOnlyKeys("CERT_CENTER", "TOILET", "WATER");
    }

    @Test
    @DisplayName("가장 가까운 노선에 연결하고, 반경 밖이면 NULL로 둔다")
    void 좌표로_노선_연결() {
        // 원본 CSV에 노선 코드가 없어 좌표로 찾는다. 노선이 먼저 있어야 한다
        bikeRouteIngestService.ingestNationalRoutes();

        RouteFacilityIngestResultDTO result = routeFacilityIngestService.ingestRouteFacilities();

        assertThat(result.getLinkedCount()).isEqualTo(2);

        String linkedRoute = jdbcClient.sql("""
                        SELECT r.route_name
                        FROM route_facility f
                        JOIN national_bike_route r ON r.national_bike_route_id = f.national_bike_route_id
                        WHERE f.facility_type = 'CERT_CENTER'
                        """)
                .query(String.class).single();
        assertThat(linkedRoute).isEqualTo("아라자전거길");

        Integer unlinked = jdbcClient
                .sql("SELECT COUNT(*) FROM route_facility WHERE national_bike_route_id IS NULL")
                .query(Integer.class).single();
        assertThat(unlinked).isEqualTo(1);       // 부산 앞바다 급수대
    }

    @Test
    @DisplayName("노선이 없으면 시설만 들어가고 연결은 비어 있다")
    void 노선_없이_적재하면_연결_안_됨() {
        RouteFacilityIngestResultDTO result = routeFacilityIngestService.ingestRouteFacilities();

        assertThat(result.getInsertedCount()).isEqualTo(3);
        assertThat(result.getLinkedCount()).isZero();
    }

    @Test
    @DisplayName("노선을 나중에 적재하고 다시 실행하면 연결만 채워진다")
    void 나중에_노선_적재하고_재실행() {
        routeFacilityIngestService.ingestRouteFacilities();
        bikeRouteIngestService.ingestNationalRoutes();

        RouteFacilityIngestResultDTO result = routeFacilityIngestService.ingestRouteFacilities();

        // 시설은 이미 다 있으므로 새로 들어간 건 없고, 연결만 계산된다
        assertThat(result.getInsertedCount()).isZero();
        assertThat(result.getLinkedCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("여러 번 실행해도 행이 늘지 않는다")
    void 재적재_멱등() {
        routeFacilityIngestService.ingestRouteFacilities();
        routeFacilityIngestService.ingestRouteFacilities();

        Integer rowCount = jdbcClient.sql("SELECT COUNT(*) FROM route_facility")
                .query(Integer.class).single();

        assertThat(rowCount).isEqualTo(3);
    }
}
