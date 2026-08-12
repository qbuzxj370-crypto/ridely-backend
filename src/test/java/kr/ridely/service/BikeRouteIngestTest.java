package kr.ridely.service;

import kr.ridely.dto.poi.BikeRouteIngestResultDTO;
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
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 국토종주 자전거길 노선 적재 통합 테스트.
 *
 * 원본 CSV(db/seed/)는 저장소에 없으므로 실행 시점에 픽스처를 만들어 쓴다.
 * 실데이터에 의존하면 파일을 내려받지 않은 환경에서 테스트가 깨진다.
 *
 * <p>파싱 자체(인코딩 판별·결손 처리)는 {@code BikeRouteCsvReaderTest}가 맡는다.
 * 여기서는 <b>파트 분리와 영속</b>만 본다.
 *
 * <p>픽스처는 원본 배포본과 같은 CP949로 쓴다.
 */
class BikeRouteIngestTest extends AbstractIntegrationTest {

    /**
     * 픽스처 CSV. 원본 형식 그대로 <b>3번째 열이 위도, 4번째 열이 경도</b>다.
     *
     * <ul>
     *   <li>코드 1(아라) : 순서가 3,1,2로 뒤섞임 → 정렬 확인. 간격 1.8~1.9km라 <b>1파트</b></li>
     *   <li>코드 2(한강종주) : 4번째 점에서 <b>32.3km 점프</b> → <b>2파트</b>로 갈려야 한다.
     *       이으면 36.8km, 나누면 4.5km다</li>
     *   <li>코드 14 : 지역 자전거길 → 제외 확인</li>
     *   <li>마지막 줄 : 형식 오류 → 건너뛰기 확인</li>
     * </ul>
     */
    private static final String FIXTURE_CSV = """
            순서,국토종주 자전거길,위도(LINE_XP),경도(LINE_YP)
            3,1,37.5900,126.6100
            1,1,37.5573,126.6034
            2,1,37.5740,126.6070
            1,2,37.5434,126.8997
            2,2,37.5350,126.9200
            3,2,37.5265,126.9339
            4,2,37.5265,127.3000
            5,2,37.5300,127.3100
            1,14,37.7700,128.8900
            2,14,37.7800,128.9000
            잘못된,행,값,입니다

            """;

    /** 잇기만 했을 때의 한강종주 길이(km). 이 값이 나오면 파트 분리가 동작하지 않은 것이다 */
    private static final double JOINED_LENGTH_KM = 36.8;

    /** 픽스처 파일 경로. 파일명은 ASCII로 둬서 실행 환경의 파일명 인코딩에 영향받지 않게 한다 */
    private static final Path FIXTURE_PATH;

    static {
        try {
            Path directory = Files.createTempDirectory("ridely-seed-test");
            FIXTURE_PATH = directory.resolve("bike-route-coords.csv");
            Files.write(FIXTURE_PATH, FIXTURE_CSV.getBytes(Charset.forName("MS949")));
            FIXTURE_PATH.toFile().deleteOnExit();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void seedFilePath(DynamicPropertyRegistry registry) {
        registry.add("ridely.seed.bike-route-coords", FIXTURE_PATH::toString);
    }

    @Autowired
    private BikeRouteIngestService bikeRouteIngestService;

    @Autowired
    private JdbcClient jdbcClient;

    /** 테스트끼리 영향을 주지 않도록 매번 비운다 (route_facility가 FK로 참조하므로 함께 정리) */
    @BeforeEach
    void clearRoutes() {
        jdbcClient.sql("DELETE FROM route_facility").update();
        jdbcClient.sql("DELETE FROM national_bike_route").update();
    }

    private <T> T queryOne(String sql, Class<T> type, String routeName) {
        return jdbcClient.sql(sql).param("name", routeName).query(type).single();
    }

    @Test
    @DisplayName("국토종주 노선만 적재하고 지역 자전거길은 제외한다")
    void 국토종주_노선만_적재() {
        BikeRouteIngestResultDTO result = bikeRouteIngestService.ingestNationalRoutes();

        assertThat(result.getRouteCount()).isEqualTo(2);
        assertThat(result.getUpsertedCount()).isEqualTo(2);
        assertThat(result.getTotalPointCount()).isEqualTo(8);
        assertThat(result.getSkippedRowCount()).isEqualTo(1);
        assertThat(result.getCharsetName()).contains("949");

        List<String> savedNames = jdbcClient
                .sql("SELECT route_name FROM national_bike_route ORDER BY route_name")
                .query(String.class)
                .list();

        // 코드 14(강릉 경포호 산소길)는 국토종주 13길이 아니므로 적재되지 않는다
        assertThat(savedNames).containsExactly("아라자전거길", "한강종주자전거길");
    }

    @Test
    @DisplayName("좌표 간격 3km를 넘으면 파트를 나눠 MultiLineString으로 저장한다")
    void 간격이_큰_지점에서_파트_분리() {
        bikeRouteIngestService.ingestNationalRoutes();

        String type = queryOne("""
                SELECT ST_GeometryType(line_geom) FROM national_bike_route WHERE route_name = :name
                """, String.class, "한강종주자전거길");
        assertThat(type).isEqualTo("ST_MultiLineString");

        // 32.3km 점프에서 갈려 2파트가 되어야 한다
        Integer parts = queryOne("""
                SELECT ST_NumGeometries(line_geom) FROM national_bike_route WHERE route_name = :name
                """, Integer.class, "한강종주자전거길");
        assertThat(parts).isEqualTo(2);

        // 파트가 갈려도 좌표는 하나도 버리지 않는다
        Integer points = queryOne("""
                SELECT ST_NPoints(line_geom) FROM national_bike_route WHERE route_name = :name
                """, Integer.class, "한강종주자전거길");
        assertThat(points).isEqualTo(5);
    }

    @Test
    @DisplayName("파트가 하나인 노선도 MultiLineString으로 저장한다 — 타입을 하나로 통일")
    void 단일_파트도_MultiLineString() {
        bikeRouteIngestService.ingestNationalRoutes();

        assertThat(queryOne("""
                SELECT ST_GeometryType(line_geom) FROM national_bike_route WHERE route_name = :name
                """, String.class, "아라자전거길")).isEqualTo("ST_MultiLineString");

        assertThat(queryOne("""
                SELECT ST_NumGeometries(line_geom) FROM national_bike_route WHERE route_name = :name
                """, Integer.class, "아라자전거길")).isEqualTo(1);
    }

    @Test
    @DisplayName("파트 사이 간격은 길이에 더하지 않는다")
    void 파트_사이는_길이에서_제외() {
        BikeRouteIngestResultDTO result = bikeRouteIngestService.ingestNationalRoutes();

        BigDecimal geometryLength = result.getRoutes().stream()
                .filter(r -> r.getRouteCode() == 2)
                .findFirst().orElseThrow()
                .getGeometryLengthKm();

        // 잇기만 하면 36.8km다. 32.3km 점프가 빠져야 5km 미만이 된다
        assertThat(geometryLength.doubleValue())
                .isLessThan(JOINED_LENGTH_KM / 2)
                .isGreaterThan(0);
    }

    @Test
    @DisplayName("total_length_km에는 공식 안내 거리를 넣는다 — 계산값이 아니다")
    void 공식_거리를_저장() {
        BikeRouteIngestResultDTO result = bikeRouteIngestService.ingestNationalRoutes();

        // 한강종주 공식 192km. 픽스처 형상은 5km도 안 되므로 계산값을 넣었다면 여기서 걸린다
        BigDecimal saved = queryOne("""
                SELECT total_length_km FROM national_bike_route WHERE route_name = :name
                """, BigDecimal.class, "한강종주자전거길");
        assertThat(saved).isEqualByComparingTo("192");

        assertThat(queryOne("""
                SELECT total_length_km FROM national_bike_route WHERE route_name = :name
                """, BigDecimal.class, "아라자전거길")).isEqualByComparingTo("21");

        // 응답에는 공식값과 실측값이 함께 담긴다. 실측은 검증용이라 저장되지 않는다
        assertThat(result.getRoutes()).allSatisfy(route -> {
            assertThat(route.getOfficialLengthKm()).isNotNull();
            assertThat(route.getGeometryLengthKm()).isNotNull();
            assertThat(route.getPartCount()).isPositive();
        });
    }

    @Test
    @DisplayName("구간 설명을 채운다")
    void 구간설명_적재() {
        bikeRouteIngestService.ingestNationalRoutes();

        assertThat(queryOne("""
                SELECT start_desc FROM national_bike_route WHERE route_name = :name
                """, String.class, "아라자전거길")).isEqualTo("아라서해갑문");
    }

    @Test
    @DisplayName("여러 번 실행해도 행이 늘지 않는다")
    void 재적재_멱등() {
        bikeRouteIngestService.ingestNationalRoutes();
        bikeRouteIngestService.ingestNationalRoutes();

        Integer rowCount = jdbcClient.sql("SELECT COUNT(*) FROM national_bike_route")
                .query(Integer.class).single();

        assertThat(rowCount).isEqualTo(2);
    }
}
