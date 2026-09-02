package kr.ridely.dao;

import kr.ridely.common.BusinessException;
import kr.ridely.common.ErrorCode;
import kr.ridely.dto.rideHistory.RidingSessionEndRequestDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * 라이딩 세션 종료 DAO.
 *
 * GPS 트랙을 track_geom(GEOMETRY)에 넣을 때 PostGIS 함수가 필요해 JdbcClient를 쓴다 (ADR-002). 나머지 조회·시작은 RidingSessionDao(MyBatis)에 있다.
 *
 * 종료를 한 번의 UPDATE로 처리하는 이유는 트랙과 측정값이 같은 순간에 함께 오기 때문이다. 측정값만 MyBatis로 쓰고 트랙만 여기서 쓰면 UPDATE가 두 번 나가고, 둘 사이에서 실패하면 종료 시각은 찍혔는데 트랙이 없는 기록이 남는다.
 *
 * ⚠️ <b>여기서 쓴 변경을 MyBatis는 알지 못한다.</b> MyBatis의 1차 캐시는 자기 UPDATE가 나갈 때만 비워지므로, 한 트랜잭션 안에서 이 DAO로 쓰고 RidingSessionDao로 다시 읽으면 갱신 전 객체가 돌아온다. 실제로 종료 응답이 종료 전 값으로 나갔다. application.yml의 local-cache-scope를 STATEMENT로 두어 막는다.
 *
 * ⚠️ RouteDao와 같은 함정 두 가지가 여기에도 있다. ST_GeomFromGeoJSON의 결과는 SRID가 0이라 ST_SetSRID로 4326을 명시해야 하고, 앱이 고도를 담아 보내면 3차원 좌표가 되므로 ST_Force2D로 Z를 떨궈야 한다.
 */
@Repository
public class RidingSessionSpatialDao {

    private static final Logger log = LoggerFactory.getLogger(RidingSessionSpatialDao.class);

    private final JdbcClient jdbcClient;

    public RidingSessionSpatialDao(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * GPS 트랙만 GeoJSON 문자열로 읽는다.
     *
     * 나머지 컬럼은 RidingSessionDao.selectById가 읽는다. 컬럼 목록을 두 곳에 두지 않으려고 여기서는 트랙 하나만 꺼낸다. 호출이 한 번 늘지만 단건 상세를 열 때뿐이다.
     *
     * 저장할 때 ST_Force2D로 Z를 떨궜으므로 좌표가 두 값으로 나온다. 지도에 그리는 데는 그편이 낫다.
     *
     * @return GeoJSON LineString 문자열. 트랙을 안 보낸 세션이거나 없거나 남의 것이면 null
     */
    public String selectTrackGeoJson(long ridingSessionId, long userId) {
        String sql = """
                SELECT ST_AsGeoJSON(track_geom)
                  FROM riding_session
                 WHERE riding_session_id = :ridingSessionId
                   AND user_id           = :userId
                """;

        return jdbcClient.sql(sql)
                .param("ridingSessionId", ridingSessionId)
                .param("userId", userId)
                .query(String.class)
                .optional()
                .orElse(null);
    }

    /**
     * 라이딩을 종료한다.
     *
     * ended_at은 서버가 NOW()로 찍는다. 기기 시계가 앞서 있으면 시작보다 이른 종료 시각이 들어가 chk_session_time에 걸린다.
     *
     * 보내지 않은 측정값은 COALESCE가 기존 값으로 되돌린다. 종료 요청은 값을 한 번에 다 보내는 것이 정상이지만, 일부만 온다고 해서 이미 들어 있는 값을 null로 지울 이유는 없다.
     *
     * WHERE에 ended_at IS NULL이 들어가 이미 끝난 세션은 걸리지 않는다. 종료를 두 번 처리하면 두 번째 요청의 값이 첫 기록을 덮어쓴다.
     *
     * @return 갱신된 행 수. 0이면 없거나·남의 것이거나·이미 끝난 것이라 호출부가 이유를 가려야 한다
     * @throws BusinessException COMMON-001 (trackGeoJson이 GeoJSON LineString이 아님)
     */
    public int end(long ridingSessionId, long userId, RidingSessionEndRequestDTO request) {
        /*
         * [핵심 구문]
         *   CAST(:x AS 타입)     ::타입 을 쓰면 이름 붙은 파라미터 파서가 :타입 을 파라미터로
         *                        오인한다. null을 넘길 때 타입 추론도 이걸로 풀린다.
         *   ST_Force2D           앱이 고도를 담아 보내면 3차원이 된다. 컬럼은 2D LineString이다.
         *   ST_SetSRID(.., 4326) ST_GeomFromGeoJSON의 결과는 SRID 0이다.
         *   바깥 COALESCE        트랙을 안 보냈으면 ST_GeomFromGeoJSON(NULL)이 NULL이 되고,
         *                        그대로 두면 기존 트랙이 지워진다.
         *   ended_at IS NULL     이미 끝난 세션을 다시 덮어쓰지 않는다.
         */
        String sql = """
                UPDATE riding_session
                   SET ended_at             = NOW(),
                       distance_km          = COALESCE(CAST(:distanceKm AS numeric), distance_km),
                       avg_speed_kmh        = COALESCE(CAST(:avgSpeedKmh AS numeric), avg_speed_kmh),
                       is_completed         = COALESCE(CAST(:isCompleted AS boolean), is_completed),
                       visited_poi_count    = COALESCE(CAST(:visitedPoiCount AS integer), visited_poi_count),
                       alert_received_count = COALESCE(CAST(:alertReceivedCount AS integer), alert_received_count),
                       track_geom           = COALESCE(
                                                  ST_Force2D(ST_SetSRID(
                                                      ST_GeomFromGeoJSON(CAST(:trackGeoJson AS text)), 4326)),
                                                  track_geom)
                 WHERE riding_session_id = :ridingSessionId
                   AND user_id           = :userId
                   AND ended_at IS NULL
                """;

        try {
            return jdbcClient.sql(sql)
                    .param("distanceKm", request.getDistanceKm())
                    .param("avgSpeedKmh", request.getAvgSpeedKmh())
                    .param("isCompleted", request.getIsCompleted())
                    .param("visitedPoiCount", request.getVisitedPoiCount())
                    .param("alertReceivedCount", request.getAlertReceivedCount())
                    .param("trackGeoJson", request.getTrackGeoJson())
                    .param("ridingSessionId", ridingSessionId)
                    .param("userId", userId)
                    .update();
        } catch (DataAccessException e) {
            /*
             * ST_GeomFromGeoJSON은 문자열이 GeoJSON이 아니면 실패한다. 잘못된 값을 보낸 것은
             * 클라이언트 문제라 500이 아니라 400이어야 한다.
             *
             * 예외 타입을 좁히지 않고 DataAccessException으로 받는 이유는 PostGIS의 파싱 실패가
             * 스프링의 어느 하위 예외로 번역되는지 확인하지 않았기 때문이다. 좁게 잡았다가
             * 빗나가면 500이 나가고 원인 찾기가 어려워진다.
             *
             * 대신 트랙을 보낸 요청에서만 400으로 바꾼다. 트랙이 없는데 이 UPDATE가 실패했다면
             * 파싱과 무관한 진짜 서버 문제라, 그것까지 400으로 덮으면 장애를 놓친다.
             */
            if (request.getTrackGeoJson() == null) {
                throw e;
            }
            log.warn("GPS 트랙을 읽지 못했다: ridingSessionId={} userId={}", ridingSessionId, userId, e);
            throw new BusinessException(ErrorCode.COMMON_001);
        }
    }
}
