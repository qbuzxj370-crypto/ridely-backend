package kr.ridely.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.ridely.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 서비스 지역 인프라 POI 전체 조회(GET /api/v1/pois/all) 통합 테스트.
 *
 * <h3>이 API의 계약이 곧 테스트 대상이다</h3>
 *
 * 앱이 적재된 시설을 통째로 받아 폰에서 GPS로 거르는 구조라, 서버는 「내 위치」를 받지도 알지도
 * 못해야 한다(LOCATION_PRIVACY_ARCHITECTURE.md). 그래서 여기서 고정하는 것은 기능보다 <b>계약</b>이다.
 *
 * <ul>
 *   <li>위치 관련 파라미터를 줘도 응답이 달라지지 않는다 - 「내 근처」를 서버가 알아채면 안 된다</li>
 *   <li>서비스 지역 밖에 적재된 것도 전부 나온다 - 위치나 지역으로 거르지 않는다</li>
 *   <li>응답 필드가 종류별 허용 목록 안에 있다 - 전체를 내려주는 구조라 필드 하나가 5,500배로
 *       불어나므로, 무거운 필드가 슬쩍 들어오는 것을 막는다</li>
 *   <li>인증 없이 열려 있다(SecurityConfig.PUBLIC_PATHS) - 빠뜨리면 401인데 원인 찾기가 어렵다</li>
 *   <li>캐시(Cache-Control·ETag·304)가 동작한다 - 전체 응답을 매번 받지 않게 하는 핵심 장치</li>
 * </ul>
 */
@AutoConfigureMockMvc
class PoiAllTest extends AbstractIntegrationTest {

    private static final String ALL_URL = "/api/v1/pois/all";

    /** 응답 항목에 허용하는 필드. 종류별로 좁게 고정한다 */
    private static final Set<String> 공통_필드 = Set.of("type", "id", "name", "lat", "lng");
    private static final Set<String> 시설_필드 = 합집합(공통_필드, "facilityType");
    private static final Set<String> 수리소_필드 = 합집합(공통_필드, "addr", "tel", "isFree", "operatingHours");
    private static final Set<String> 사고다발지_필드 = 합집합(공통_필드, "dangerLevel", "occurrenceCount", "deathCount");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private ObjectMapper objectMapper;

    private long 서울_지역번호;

    @BeforeEach
    void 데이터_준비() {
        jdbcClient.sql("TRUNCATE TABLE route_facility RESTART IDENTITY CASCADE").update();
        jdbcClient.sql("TRUNCATE TABLE bike_station RESTART IDENTITY CASCADE").update();
        jdbcClient.sql("TRUNCATE TABLE repair_shop RESTART IDENTITY CASCADE").update();
        jdbcClient.sql("TRUNCATE TABLE accident_zone RESTART IDENTITY").update();

        서울_지역번호 = jdbcClient.sql("SELECT region_id FROM region WHERE region_code = '11'")
                .query(Long.class)
                .single();

        시설("WATER", "여의도 급수대", 126.9339, 37.5265);
        시설("TOILET", "여의도 화장실", 126.9465, 37.5265);
        수리소("영등포 무료 수리센터", 126.8962, 37.5265);
        대여소("ST-001", "여의나루역 대여소", 126.9350, 37.5270, true);
        대여소("ST-002", "폐쇄된 대여소", 126.9360, 37.5280, false);
        사고다발지("afos-1", 2024, "최신 연도 사고다발지", 126.9400, 37.5300);
        사고다발지("afos-2", 2023, "지난 연도 사고다발지", 126.9500, 37.5310);
    }

    @Test
    @DisplayName("토큰 없이 조회할 수 있다")
    void 공개_경로다() throws Exception {
        // ★ SecurityConfig.PUBLIC_PATHS에서 빠지면 여기서 401(COMMON-002)이 난다
        mockMvc.perform(get(ALL_URL))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("전 종류를 종류 순서대로 담는다 - 폐쇄된 대여소와 지난 연도 사고다발지는 뺀다")
    void 전_종류_종류순() throws Exception {
        mockMvc.perform(get(ALL_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalCount").value(5))
                // 시설(번호순) → 수리소 → 따릉이 → 사고다발지
                .andExpect(jsonPath("$.data.items[0].type").value("ROUTE_FACILITY"))
                .andExpect(jsonPath("$.data.items[0].facilityType").value("WATER"))
                .andExpect(jsonPath("$.data.items[1].facilityType").value("TOILET"))
                .andExpect(jsonPath("$.data.items[2].type").value("REPAIR_SHOP"))
                .andExpect(jsonPath("$.data.items[3].type").value("BIKE_STATION"))
                .andExpect(jsonPath("$.data.items[3].name").value("여의나루역 대여소"))
                .andExpect(jsonPath("$.data.items[4].type").value("ACCIDENT_ZONE"))
                .andExpect(jsonPath("$.data.items[4].name").value("최신 연도 사고다발지"));
    }

    @Test
    @DisplayName("위치 관련 파라미터를 줘도 응답이 달라지지 않는다")
    void 위치_파라미터는_무시된다() throws Exception {
        // ★ 이 API는 위치를 받지 않는 계약이다. 서버가 lat/lng/radiusM에 반응하기 시작하면
        //   「내 근처」를 서버가 알게 되는 것이라 위치정보 서버 무전송 원칙이 깨진다.
        String 기본 = 본문(get(ALL_URL));
        String 위치_포함 = 본문(get(ALL_URL)
                .param("lat", "37.5265").param("lng", "126.9339")
                .param("radiusM", "10").param("types", "WATER"));

        assertThat(위치_포함).isEqualTo(기본);
    }

    @Test
    @DisplayName("서비스 지역 밖에 적재된 것도 전부 나온다")
    void 지역으로_거르지_않는다() throws Exception {
        시설("WATER", "부산 급수대", 129.1603, 35.1587);

        mockMvc.perform(get(ALL_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(6))
                .andExpect(jsonPath("$.data.items[?(@.name == '부산 급수대')]").isNotEmpty());
    }

    @Test
    @DisplayName("항목 필드가 종류별 허용 목록 안에 있고 무거운 필드가 없다")
    void 슬림한_응답이다() throws Exception {
        JsonNode items = objectMapper.readTree(본문(get(ALL_URL))).path("data").path("items");

        List<String> 위반 = new ArrayList<>();
        for (JsonNode item : items) {
            Set<String> 허용 = switch (item.path("type").asText()) {
                case "ROUTE_FACILITY" -> 시설_필드;
                case "REPAIR_SHOP" -> 수리소_필드;
                case "BIKE_STATION" -> 공통_필드;
                case "ACCIDENT_ZONE" -> 사고다발지_필드;
                default -> Set.of();
            };
            item.fieldNames().forEachRemaining(필드 -> {
                if (!허용.contains(필드)) {
                    위반.add(item.path("type").asText() + "." + 필드);
                }
            });
        }

        // 폴리곤·거리·거치대 수 등이 슬쩍 들어오면 여기서 잡힌다. 전체 조회는 항목 하나의 크기가
        // 곧 모든 사용자의 다운로드 크기에 곱해진다
        assertThat(위반).as("허용 목록 밖 필드").isEmpty();
        assertThat(items.size()).isEqualTo(5);
    }

    @Test
    @DisplayName("Cache-Control(공개, 1시간)과 ETag를 준다")
    void 캐시_헤더() throws Exception {
        MvcResult result = mockMvc.perform(get(ALL_URL)).andExpect(status().isOk()).andReturn();

        String cacheControl = result.getResponse().getHeader("Cache-Control");
        assertThat(cacheControl).contains("max-age=3600").contains("public");
        // 스프링 시큐리티의 기본 「no-store」가 덮어쓰면 캐시가 통째로 무의미해진다
        assertThat(cacheControl).doesNotContain("no-store");
        assertThat(result.getResponse().getHeader("ETag")).isNotBlank();
    }

    @Test
    @DisplayName("바뀐 게 없으면 If-None-Match로 다시 물었을 때 본문 없이 304다")
    void 변경_없으면_304() throws Exception {
        String etag = mockMvc.perform(get(ALL_URL)).andReturn().getResponse().getHeader("ETag");
        assertThat(etag).isNotBlank();

        MvcResult 다시 = mockMvc.perform(get(ALL_URL).header("If-None-Match", etag))
                .andExpect(status().isNotModified())
                .andReturn();
        assertThat(다시.getResponse().getContentAsString()).isEmpty();
    }

    @Test
    @DisplayName("데이터가 바뀌면 ETag도 바뀐다 - 304로 옛 목록을 계속 쓰게 되면 안 된다")
    void 변경되면_ETag가_바뀐다() throws Exception {
        String 이전 = mockMvc.perform(get(ALL_URL)).andReturn().getResponse().getHeader("ETag");

        시설("WATER", "새로 적재된 급수대", 126.9000, 37.5000);

        MvcResult 이후 = mockMvc.perform(get(ALL_URL).header("If-None-Match", 이전))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(이후.getResponse().getHeader("ETag")).isNotEqualTo(이전);
    }

    @Test
    @DisplayName("적재된 것이 없어도 200에 빈 배열이다")
    void 비어_있어도_200이다() throws Exception {
        jdbcClient.sql("TRUNCATE TABLE route_facility RESTART IDENTITY CASCADE").update();
        jdbcClient.sql("TRUNCATE TABLE bike_station RESTART IDENTITY CASCADE").update();
        jdbcClient.sql("TRUNCATE TABLE repair_shop RESTART IDENTITY CASCADE").update();
        jdbcClient.sql("TRUNCATE TABLE accident_zone RESTART IDENTITY").update();

        mockMvc.perform(get(ALL_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalCount").value(0))
                .andExpect(jsonPath("$.data.items").isEmpty());
    }

    @Test
    @DisplayName("기존 반경 조회(/pois/nearby)는 그대로 동작한다")
    void 기존_반경_조회는_유지된다() throws Exception {
        mockMvc.perform(get("/api/v1/pois/nearby")
                        .param("lat", "37.5265").param("lng", "126.9339").param("radiusM", "1000")
                        .param("types", "WATER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].name").value("여의도 급수대"))
                .andExpect(jsonPath("$.data.items[0].distanceM").value(0));
    }

    // ===== 도우미 =====

    private String 본문(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder 요청) throws Exception {
        return mockMvc.perform(요청).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private static Set<String> 합집합(Set<String> 기본, String... 추가) {
        java.util.HashSet<String> 결과 = new java.util.HashSet<>(기본);
        결과.addAll(List.of(추가));
        return Set.copyOf(결과);
    }

    private void 시설(String type, String name, double lng, double lat) {
        jdbcClient.sql("""
                        INSERT INTO route_facility (facility_type, facility_name, geom)
                        VALUES (:type, :name, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326))
                        """)
                .param("type", type).param("name", name)
                .param("lng", lng).param("lat", lat)
                .update();
    }

    private void 수리소(String name, double lng, double lat) {
        jdbcClient.sql("""
                        INSERT INTO repair_shop (shop_name, geom, addr, tel, is_free, operating_hours)
                        VALUES (:name, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326),
                                '서울 영등포구', '02-000-0000', TRUE, '평일 09:00~18:00')
                        """)
                .param("name", name)
                .param("lng", lng).param("lat", lat)
                .update();
    }

    private void 대여소(String code, String name, double lng, double lat, boolean active) {
        jdbcClient.sql("""
                        INSERT INTO bike_station (station_code, station_name, geom, rack_count, is_active)
                        VALUES (:code, :name, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326), 10, :active)
                        """)
                .param("code", code).param("name", name)
                .param("lng", lng).param("lat", lat)
                .param("active", active)
                .update();
    }

    private void 사고다발지(String afosFid, int year, String name, double lng, double lat) {
        jdbcClient.sql("""
                        INSERT INTO accident_zone (
                            region_id, afos_fid, data_year, spot_name, danger_level,
                            occurrence_count, death_count, center_geom, polygon_geom
                        ) VALUES (
                            :regionId, :afosFid, :year, :name, 'DANGER',
                            10, 1,
                            ST_SetSRID(ST_MakePoint(:lng, :lat), 4326),
                            ST_SetSRID(ST_MakeEnvelope(:lng - 0.001, :lat - 0.001, :lng + 0.001, :lat + 0.001), 4326)
                        )
                        """)
                .param("regionId", 서울_지역번호)
                .param("afosFid", afosFid).param("year", year).param("name", name)
                .param("lng", lng).param("lat", lat)
                .update();
    }
}
