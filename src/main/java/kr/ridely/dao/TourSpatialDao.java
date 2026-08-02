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
                .query((rs, rowNum) -> {
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
                })
                .list();
    }
}
