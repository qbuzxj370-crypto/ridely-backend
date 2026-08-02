package kr.ridely.controller;

import kr.ridely.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 주변 관광지 조회 API 통합 테스트.
 * 컨트롤러부터 실제 PostGIS 검색까지 전 구간을 검증한다.
 */
@AutoConfigureMockMvc
class TourControllerTest extends AbstractIntegrationTest {

    private static final String URL = "/api/v1/tours/nearby";

    /** 선유도공원 */
    private static final String 중심_위도 = "37.5434";
    private static final String 중심_경도 = "126.8997";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbcClient;

    @BeforeEach
    void 데이터_준비() {
        jdbcClient.sql("TRUNCATE TABLE tour_attraction RESTART IDENTITY CASCADE").update();

        insert("2001", "12", "선유도공원", 126.8997, 37.5434);          // 0m
        insert("2002", "39", "양화한강공원 매점", 126.9100, 37.5450);   // 약 1.0km
        insert("2003", "14", "여의도 문화시설", 126.9250, 37.5300);     // 약 2.5km
        insert("2004", "12", "반포한강공원", 126.9954, 37.5100);        // 약 9km
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
    @DisplayName("반경 내 관광지를 가까운 순으로 반환한다")
    void 조회_성공() throws Exception {
        mockMvc.perform(get(URL)
                        .param("lat", 중심_위도)
                        .param("lng", 중심_경도)
                        .param("radiusM", "5000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                // 조회 조건을 그대로 돌려준다
                .andExpect(jsonPath("$.data.center.lat").value(37.5434))
                .andExpect(jsonPath("$.data.center.lng").value(126.8997))
                .andExpect(jsonPath("$.data.radiusM").value(5000))
                // 9km 지점(반포)은 제외 → 3건
                .andExpect(jsonPath("$.data.totalCount").value(3))
                .andExpect(jsonPath("$.data.items[0].title").value("선유도공원"))
                .andExpect(jsonPath("$.data.items[0].distanceM").value(0))
                .andExpect(jsonPath("$.data.items[2].title").value("여의도 문화시설"));
    }

    @Test
    @DisplayName("관광타입을 지정하면 해당 타입만 반환한다")
    void 타입_필터() throws Exception {
        mockMvc.perform(get(URL)
                        .param("lat", 중심_위도)
                        .param("lng", 중심_경도)
                        .param("radiusM", "5000")
                        .param("contentTypeIds", "12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.items[0].contentTypeId").value("12"));
    }

    @Test
    @DisplayName("반경을 지정하지 않으면 기본값 1km를 적용한다")
    void 반경_기본값() throws Exception {
        mockMvc.perform(get(URL)
                        .param("lat", 중심_위도)
                        .param("lng", 중심_경도))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.radiusM").value(1000))
                // 1km 안에는 선유도공원(0m)과 양화한강공원 매점(약 1.0km 경계) 중 선유도만 확실
                .andExpect(jsonPath("$.data.items[0].title").value("선유도공원"));
    }

    @Test
    @DisplayName("반경 내에 결과가 없으면 POI-001을 반환한다")
    void 결과_없음() throws Exception {
        // 부산 앞바다
        mockMvc.perform(get(URL)
                        .param("lat", "35.1")
                        .param("lng", "129.0")
                        .param("radiusM", "1000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("POI-001"));
    }

    @Test
    @DisplayName("반경이 상한을 넘으면 COMMON-001을 반환한다")
    void 반경_상한_초과() throws Exception {
        mockMvc.perform(get(URL)
                        .param("lat", 중심_위도)
                        .param("lng", 중심_경도)
                        .param("radiusM", "99999"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON-001"))
                .andExpect(jsonPath("$.error.details.radiusM").isNotEmpty());
    }

    @Test
    @DisplayName("좌표가 한반도 범위를 벗어나면 COMMON-001을 반환한다")
    void 좌표_범위_초과() throws Exception {
        mockMvc.perform(get(URL)
                        .param("lat", "10.0")
                        .param("lng", "126.8997")
                        .param("radiusM", "1000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON-001"))
                .andExpect(jsonPath("$.error.details.lat").isNotEmpty());
    }

    @Test
    @DisplayName("필수 파라미터가 없으면 COMMON-001을 반환한다")
    void 파라미터_누락() throws Exception {
        mockMvc.perform(get(URL).param("lat", 중심_위도))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON-001"))
                .andExpect(jsonPath("$.error.details.lng").isNotEmpty());
    }

    @Test
    @DisplayName("좌표 형식이 잘못되면 COMMON-001을 반환한다")
    void 좌표_형식_오류() throws Exception {
        mockMvc.perform(get(URL)
                        .param("lat", "abc")
                        .param("lng", 중심_경도)
                        .param("radiusM", "1000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON-001"))
                .andExpect(jsonPath("$.error.details.lat").isNotEmpty());
    }

    @Test
    @DisplayName("인증 없이 조회할 수 있다")
    void 비회원_접근() throws Exception {
        // 토큰 없이 호출 — 401이 아니어야 한다
        mockMvc.perform(get(URL)
                        .param("lat", 중심_위도)
                        .param("lng", 중심_경도)
                        .param("radiusM", "5000"))
                .andExpect(status().isOk());
    }
}
