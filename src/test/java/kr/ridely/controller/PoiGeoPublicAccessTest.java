package kr.ridely.controller;

import kr.ridely.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 인프라 POI·장소 검색 API 통합 테스트.
 *
 * <h3>공개 경로 확인이 이 클래스의 첫 목적이다</h3>
 *
 * {@code @SecurityRequirements}는 Swagger 문서에만 영향을 주고 실제 인가는 {@code SecurityConfig.PUBLIC_PATHS}가 정한다. 새 API를 만들면서 목록에 넣는 것을 잊으면 <b>401이 나는데 컨트롤러도 서비스도 멀쩡해서 원인을 찾기 어렵다.</b>
 *
 * 2026-09-15에 {@code /api/v1/geo/**}를 빠뜨려 서버를 띄운 뒤에야 알았다. 그 전에 {@code /tours/**}로 같은 일을 한 번 겪었고 {@code SecurityConfig} javadoc에 경고까지 적혀 있었다. <b>경고문으로는 두 번 다 못 막았으므로 테스트로 고정한다.</b>
 *
 * 나머지는 컨트롤러부터 PostGIS 검색까지 전 구간을 본다. 장소 검색은 외부 호출이라 여기서 다루지 않는다 - 결과가 Kakao 응답과 네트워크에 달려 있어 테스트가 그것들에 묶인다.
 */
@AutoConfigureMockMvc
class PoiGeoPublicAccessTest extends AbstractIntegrationTest {

    private static final String POI_URL = "/api/v1/pois/nearby";
    private static final String GEO_URL = "/api/v1/geo/search";

    /** 여의도한강공원 */
    private static final String 중심_위도 = "37.5265";
    private static final String 중심_경도 = "126.9339";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbcClient;

    @BeforeEach
    void 데이터_준비() {
        jdbcClient.sql("TRUNCATE TABLE route_facility RESTART IDENTITY CASCADE").update();
        jdbcClient.sql("TRUNCATE TABLE bike_station RESTART IDENTITY CASCADE").update();

        시설("WATER", "가까운 급수대", 126.9339, 37.5265);
        시설("TOILET", "중간 화장실", 126.9465, 37.5265);
        대여소("ST-001", "여의나루역 대여소", 126.9350, 37.5270);
    }

    @Test
    @DisplayName("토큰 없이 POI를 조회할 수 있다")
    void POI는_공개_경로다() throws Exception {
        // ★ SecurityConfig.PUBLIC_PATHS에서 빠지면 여기서 401(COMMON-002)이 난다
        mockMvc.perform(요청("1000", null))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("토큰 없이 장소를 검색할 수 있다")
    void 장소_검색도_공개_경로다() throws Exception {
        // 외부 호출 결과는 보지 않는다. Kakao가 응답하든 말든 테스트가 그것에 묶이면 안 된다.
        // 여기서 고정하려는 것은 인가뿐이라 401이 아니기만 하면 된다 -
        // 502(GEO-001)가 와도 시큐리티는 지나왔다는 뜻이다
        mockMvc.perform(get(GEO_URL).param("query", "여의도"))
                .andExpect(result -> assertThat(result.getResponse().getStatus())
                        .as("인가를 통과해야 한다")
                        .isNotEqualTo(401));
    }

    @Test
    @DisplayName("종류에 상관없이 거리순으로 합쳐 준다")
    void 거리순_통합() throws Exception {
        mockMvc.perform(요청("1000", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].name").value("가까운 급수대"))
                .andExpect(jsonPath("$.data.items[1].type").value("BIKE_STATION"))
                .andExpect(jsonPath("$.data.center.lat").value(37.5265))
                .andExpect(jsonPath("$.data.radiusM").value(1000));
    }

    @Test
    @DisplayName("지정한 종류만 나온다")
    void 종류_지정() throws Exception {
        mockMvc.perform(요청("1000", "BIKE_STATION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.items[0].type").value("BIKE_STATION"));
    }

    @Test
    @DisplayName("반경 내 결과가 없어도 200에 빈 배열이다")
    void 결과_없음은_200이다() throws Exception {
        // ★ 관광지 조회가 POI-001(404)을 내는 것과 갈리는 지점이다.
        //   수리소는 서울 한강 구간에 24곳뿐이라 0건이 이 레이어의 평소 상태다
        mockMvc.perform(요청("1000", "REPAIR_SHOP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalCount").value(0))
                .andExpect(jsonPath("$.data.items").isEmpty());
    }

    @Test
    @DisplayName("서비스 지역 밖은 ROUTE-003이다")
    void 지역_밖() throws Exception {
        // 지역 안 0건과 달리 지도를 아무리 옮겨도 안 나온다. 같은 응답을 주면 안 된다
        mockMvc.perform(get(POI_URL).param("lat", "35.1587").param("lng", "129.1603"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("ROUTE-003"));
    }

    @Test
    @DisplayName("반경 상한을 넘으면 COMMON-001이다")
    void 반경_상한() throws Exception {
        mockMvc.perform(요청("9999", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON-001"));
    }

    // ===== 도우미 =====

    private MockHttpServletRequestBuilder 요청(String radiusM, String types) {
        MockHttpServletRequestBuilder builder = get(POI_URL)
                .param("lat", 중심_위도)
                .param("lng", 중심_경도)
                .param("radiusM", radiusM);
        return types == null ? builder : builder.param("types", types);
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

    private void 대여소(String code, String name, double lng, double lat) {
        jdbcClient.sql("""
                        INSERT INTO bike_station (station_code, station_name, geom, rack_count)
                        VALUES (:code, :name, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326), 10)
                        """)
                .param("code", code).param("name", name)
                .param("lng", lng).param("lat", lat)
                .update();
    }
}
