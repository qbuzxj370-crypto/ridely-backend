package kr.ridely.dao;

import kr.ridely.dto.tour.TourAttractionDTO;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 관광 콘텐츠 공간 조회 DAO.
 *
 * PostGIS 연산자(ST_DWithin·ST_Distance)를 사용하므로 MyBatis가 아닌 JdbcClient를 사용한다
 */
@Repository
public class TourSpatialDao {

    private final JdbcClient jdbcClient;

    public TourSpatialDao(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * 좌표 반경 내 관광 콘텐츠를 가까운 순으로 조회한다.
     *
     * @param lng            중심 경도
     * @param lat            중심 위도
     * @param radiusM        반경 (m)
     * @param contentTypeIds 조회할 관광타입 목록 (12=관광지, 14=문화시설, 39=음식점)
     * @param limit          최대 반환 건수
     */
    public List<TourAttractionDTO> findNearby(double lng, double lat, int radiusM,
                                              List<String> contentTypeIds, int limit) {

        /*
         * [무엇을 하는 쿼리인가]
         *   중심 좌표에서 반경 안에 있는 관광지를 가까운 순으로 뽑는다.
         *
         * [핵심 구문]
         *   geom::geography      geometry(도 단위)를 geography(미터 단위)로 변환.
         *                        이걸 붙여야 반경·거리를 미터로 다룰 수 있다.
         *                        생략하면 :radiusM이 "도(degree)"로 해석돼 지구 몇 바퀴가 된다.
         *   ST_DWithin(A, B, R)  A와 B의 거리가 R 이내인지 판정. GIST 인덱스를 타는 형태라
         *                        ST_Distance(...) < R 로 쓰는 것보다 빠르다
         *                        (idx_tour_geom_gist 활용).
         *   ST_Distance(A, B)    실제 거리(m). 정렬·응답 표시에 사용.
         *   ST_X / ST_Y          저장된 점에서 경도·위도를 다시 꺼낸다.
         *   ANY(:contentTypeIds) 타입 목록을 배열 파라미터 하나로 전달 (IN 절 동적 조립 회피).
         *
         * [주의]
         *   ST_MakePoint는 (경도, 위도) 순서다. 위도·경도로 넣으면 조용히 엉뚱한 곳을 가리킨다.
         */
        String sql = """
                SELECT
                    tour_attraction_id,
                    content_id,
                    content_type_id,
                    title,
                    ST_Y(geom) AS lat,
                    ST_X(geom) AS lng,
                    addr1,
                    addr2,
                    tel,
                    first_image_url,
                    thumbnail_url,
                    overview,
                    event_start_date,
                    event_end_date,
                    ROUND(
                        ST_Distance(
                            geom::geography,
                            ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography
                        )
                    )::int AS distance_m
                FROM tour_attraction
                WHERE ST_DWithin(
                          geom::geography,
                          ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography,
                          :radiusM
                      )
                  AND content_type_id = ANY(:contentTypeIds)
                ORDER BY distance_m
                LIMIT :limit
                """;

        return jdbcClient.sql(sql)
                .param("lng", lng)
                .param("lat", lat)
                .param("radiusM", radiusM)
                .param("contentTypeIds", contentTypeIds.toArray(new String[0]))
                .param("limit", limit)
                .query(TourSpatialDao::mapRow)
                .list();
    }

    /**
     * 출발지~도착지를 이은 축 주변의 관광 콘텐츠를 근거리·원거리를 섞어 조회한다.
     *
     * findNearby는 한 점 주변을 보는데, 코스 추천에서는 그걸로 부족하다. 출발점 반경만 보면 도착지 쪽 관광지가 통째로 빠지고, 그렇다고 중간점에서 원을 크게 그리면 축에서 멀리 벗어난 곳까지 들어온다. 두 점을 이은 선에서의 거리로 재야 경로 주변만 남는다.
     *
     * 도착지가 없으면(순환 코스) 축을 만들 수 없으므로 출발점 하나를 기준으로 삼는다. 이때 반경은 호출부가 목표 거리에서 유도해 넘긴다.
     *
     * 정렬은 가까운 순이 아니다. 가까운 순 등수와 먼 순 등수 중 작은 값으로 정렬해 양 끝에서 번갈아 집는다. 가까운 순으로만 자르면 축에 붙은 관광지만 올라와 코스가 목표 거리에 못 미친다 — PoiSpatialDao.PICK_BOTH_ENDS에 같은 판단과 실측 근거가 있다.
     *
     * @param endLng    도착지 경도. null이면 출발점 반경만 본다
     * @param endLat    도착지 위도. null이면 위와 같다
     * @param corridorM 축에서 이 거리 안까지 후보로 본다 (m)
     */
    public List<TourAttractionDTO> findAlongCorridor(double startLng, double startLat,
                                                     Double endLng, Double endLat,
                                                     int corridorM, List<String> contentTypeIds, int limit) {
        /*
         * :hasEnd를 따로 받는 이유 — :endLng를 NULL로 넘기면 PostgreSQL이 CASE 안에서
         * 파라미터 타입을 추론하지 못해 "could not determine data type of parameter"로 깨진다.
         * 도착지가 없으면 Java가 출발지 좌표를 채워 넣고 사용 여부는 이 플래그로 가른다.
         * PoiSpatialDao와 같은 방식이다.
         */
        String sql = """
                WITH corridor AS (
                    SELECT (CASE
                              WHEN :hasEnd THEN ST_MakeLine(
                                       ST_SetSRID(ST_MakePoint(:startLng, :startLat), 4326),
                                       ST_SetSRID(ST_MakePoint(:endLng,   :endLat),   4326))
                              ELSE ST_SetSRID(ST_MakePoint(:startLng, :startLat), 4326)
                            END)::geography AS g
                )
                , nearby AS (
                    SELECT
                        t.tour_attraction_id,
                        t.content_id,
                        t.content_type_id,
                        t.title,
                        ST_Y(t.geom) AS lat,
                        ST_X(t.geom) AS lng,
                        t.addr1,
                        t.addr2,
                        t.tel,
                        t.first_image_url,
                        t.thumbnail_url,
                        t.overview,
                        t.event_start_date,
                        t.event_end_date,
                        ROUND(ST_Distance(t.geom::geography, c.g))::int AS distance_m
                    FROM tour_attraction t, corridor c
                    WHERE ST_DWithin(t.geom::geography, c.g, :corridorM)
                      AND t.content_type_id = ANY(:contentTypeIds)
                )
                SELECT * FROM (
                    SELECT n.*,
                           LEAST(ROW_NUMBER() OVER (ORDER BY distance_m),
                                 ROW_NUMBER() OVER (ORDER BY distance_m DESC)) AS pick_rank
                    FROM nearby n
                ) r
                ORDER BY pick_rank, distance_m
                LIMIT :limit
                """;

        boolean hasEnd = endLng != null && endLat != null;
        return jdbcClient.sql(sql)
                .param("startLng", startLng)
                .param("startLat", startLat)
                .param("endLng", hasEnd ? endLng : startLng)
                .param("endLat", hasEnd ? endLat : startLat)
                .param("hasEnd", hasEnd)
                .param("corridorM", corridorM)
                .param("contentTypeIds", contentTypeIds.toArray(new String[0]))
                .param("limit", limit)
                .query(TourSpatialDao::mapRow)
                .list();
    }

    private static TourAttractionDTO mapRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        TourAttractionDTO dto = new TourAttractionDTO();
        dto.setTourAttractionId(rs.getLong("tour_attraction_id"));
        dto.setContentId(rs.getString("content_id"));
        dto.setContentTypeId(rs.getString("content_type_id"));
        dto.setTitle(rs.getString("title"));
        dto.setLat(rs.getDouble("lat"));
        dto.setLng(rs.getDouble("lng"));
        dto.setAddr1(rs.getString("addr1"));
        dto.setAddr2(rs.getString("addr2"));
        dto.setTel(rs.getString("tel"));
        dto.setFirstImageUrl(rs.getString("first_image_url"));
        dto.setThumbnailUrl(rs.getString("thumbnail_url"));
        dto.setOverview(rs.getString("overview"));
        dto.setEventStartDate(rs.getObject("event_start_date", java.time.LocalDate.class));
        dto.setEventEndDate(rs.getObject("event_end_date", java.time.LocalDate.class));
        dto.setDistanceM(rs.getInt("distance_m"));
        return dto;
    }
}
