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
 * 관광지 상세 조회 API 통합 테스트.
 *
 * 추천 코스의 경유지를 눌렀을 때 쓰는 경로라, 경유지가 들고 있지 않은 필드(사진·주소·개요)가
 * 실제로 채워져 나오는지와 값이 없을 때의 응답 모양을 함께 고정한다.
 */
@AutoConfigureMockMvc
class TourDetailTest extends AbstractIntegrationTest {

    private static final String URL = "/api/v1/tours/";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbcClient;

    private long 선유도공원_번호;
    private long 매점_번호;

    @BeforeEach
    void 데이터_준비() {
        jdbcClient.sql("TRUNCATE TABLE tour_attraction RESTART IDENTITY CASCADE").update();

        선유도공원_번호 = insertFull();
        매점_번호 = insertMinimal();
    }

    /** 사진·주소·전화·개요가 모두 있는 콘텐츠 */
    private long insertFull() {
        return jdbcClient.sql("""
                        INSERT INTO tour_attraction
                            (content_id, content_type_id, title, geom,
                             addr1, addr2, tel, first_image_url, thumbnail_url, overview)
                        VALUES ('2001', '12', '선유도공원',
                                ST_SetSRID(ST_MakePoint(126.8997, 37.5434), 4326),
                                '서울특별시 영등포구 선유로 343', '선유도공원', '02-2631-9368',
                                'https://example.test/seonyudo.jpg',
                                'https://example.test/seonyudo_thumb.jpg',
                                '한강 위의 정수장을 재생한 생태공원이다.')
                        RETURNING tour_attraction_id
                        """)
                .query(Long.class).single();
    }

    /** 좌표와 이름만 있는 콘텐츠. 적재된 데이터에 이런 건이 많다 */
    private long insertMinimal() {
        return jdbcClient.sql("""
                        INSERT INTO tour_attraction (content_id, content_type_id, title, geom)
                        VALUES ('2002', '39', '양화한강공원 매점',
                                ST_SetSRID(ST_MakePoint(126.9100, 37.5450), 4326))
                        RETURNING tour_attraction_id
                        """)
                .query(Long.class).single();
    }

    @Test
    @DisplayName("번호로 관광지 상세를 반환한다")
    void 조회_성공() throws Exception {
        mockMvc.perform(get(URL + 선유도공원_번호))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.tourAttractionId").value(선유도공원_번호))
                .andExpect(jsonPath("$.data.title").value("선유도공원"))
                .andExpect(jsonPath("$.data.contentTypeId").value("12"))
                // 경유지(waypoints_json)가 들고 있지 않은 것들이 여기서 채워진다
                .andExpect(jsonPath("$.data.addr1").value("서울특별시 영등포구 선유로 343"))
                .andExpect(jsonPath("$.data.tel").value("02-2631-9368"))
                .andExpect(jsonPath("$.data.firstImageUrl").value("https://example.test/seonyudo.jpg"))
                .andExpect(jsonPath("$.data.overview").value("한강 위의 정수장을 재생한 생태공원이다."))
                // 좌표는 geom에서 다시 꺼내 온다
                .andExpect(jsonPath("$.data.lat").value(37.5434))
                .andExpect(jsonPath("$.data.lng").value(126.8997));
    }

    @Test
    @DisplayName("기준점이 없으므로 distanceM은 응답에 담기지 않는다")
    void 거리_없음() throws Exception {
        // 전역 설정이 non_null이라 값이 null이면 필드 자체가 빠진다.
        // 0으로 채워 나가면 화면이 "0m"로 읽으므로 그 회귀를 여기서 막는다.
        mockMvc.perform(get(URL + 선유도공원_번호))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.distanceM").doesNotExist());
    }

    @Test
    @DisplayName("원본에 없는 필드는 응답에서 빠진다")
    void 빈_필드_생략() throws Exception {
        mockMvc.perform(get(URL + 매점_번호))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("양화한강공원 매점"))
                .andExpect(jsonPath("$.data.addr1").doesNotExist())
                .andExpect(jsonPath("$.data.tel").doesNotExist())
                .andExpect(jsonPath("$.data.firstImageUrl").doesNotExist())
                .andExpect(jsonPath("$.data.overview").doesNotExist());
    }

    @Test
    @DisplayName("없는 번호는 COMMON-004")
    void 없는_번호() throws Exception {
        mockMvc.perform(get(URL + (선유도공원_번호 + 9999)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON-004"));
    }

    @Test
    @DisplayName("리터럴 경로인 /nearby가 번호 경로보다 먼저 매칭된다")
    void 경로_충돌_없음() throws Exception {
        // /{tourAttractionId}를 추가하면서 /nearby가 가려지지 않는지 확인한다.
        // riding-sessions에서 /summary와 /{id}를 함께 둘 때 같은 것을 확인했다.
        mockMvc.perform(get(URL + "nearby")
                        .param("lat", "37.5434")
                        .param("lng", "126.8997")
                        .param("radiusM", "5000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray());
    }
}
