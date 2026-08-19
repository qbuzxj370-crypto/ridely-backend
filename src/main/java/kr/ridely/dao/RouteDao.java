package kr.ridely.dao;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.ridely.common.BusinessException;
import kr.ridely.common.ErrorCode;
import kr.ridely.dto.route.PassingDangerZoneDTO;
import kr.ridely.dto.route.RouteRecommendRequestDTO;
import kr.ridely.dto.route.RouteRecommendResponseDTO;
import kr.ridely.dto.route.WaypointDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 추천 코스 DAO.
 *
 * PostGIS 함수(ST_GeomFromGeoJSON·ST_Force2D·ST_AsGeoJSON)를 사용하므로 JdbcClient를 쓴다 (ADR-002).
 *
 * ⚠️ 두 가지 함정이 있다.
 *
 * 첫째, ORS가 주는 경로 형상은 3차원이다. elevation=true로 호출해 좌표가 [경도, 위도, 고도] 세 값으로 오는데 route_geom 컬럼은 2D LineString이다. 그대로 넣으면 "Geometry has Z dimension but column does not"로 깨진다. ST_Force2D로 Z를 떨군다.
 *
 * 둘째, ST_GeomFromGeoJSON은 SRID를 0으로 만든다. GeoJSON에 crs 항목이 없기 때문이고 ORS 응답에는 없다. ST_SetSRID로 4326을 명시하지 않으면 컬럼 제약에 걸린다.
 */
@Repository
public class RouteDao {

    private static final Logger log = LoggerFactory.getLogger(RouteDao.class);

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    public RouteDao(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 추천 결과를 저장하고 생성된 번호와 시각을 돌려준다.
     *
     * @param userId 로그인 사용자 번호. 비회원이면 null
     * @return 생성된 (recommendedRouteId, createdAt)
     */
    public Saved insert(RouteRecommendRequestDTO request, RouteRecommendResponseDTO response,
                        Long userId, String llmProvider) {

        /*
         * [핵심 구문]
         *   ST_Force2D          ORS 형상의 Z를 떨군다. 이게 없으면 컬럼 차원이 안 맞아 깨진다.
         *   ST_SetSRID(.., 4326) ST_GeomFromGeoJSON의 결과는 SRID 0이다. 명시해야 한다.
         *   CAST(:x AS jsonb)   ::jsonb 를 쓰면 이름 붙은 파라미터 파서가 :jsonb 를
         *                       파라미터로 오인한다. CAST 문법을 쓴다.
         *   CASE WHEN :hasEnd   도착지가 없으면 end_geom을 NULL로 둔다. :endLng를 NULL로
         *                       넘기면 파라미터 타입 추론이 실패하므로 플래그로 가른다.
         */
        String sql = """
                INSERT INTO recommended_route (
                    user_id, start_geom, end_geom, target_distance_km,
                    priority_convenience, priority_exercise, priority_scenery,
                    avoid_danger_zones_applied, route_geom,
                    total_distance_km, estimated_duration_min, total_ascent_m, total_descent_m,
                    intensity_level, waypoints_json, passing_danger_zones_json,
                    ai_title, ai_highlights, ai_coach_comment,
                    ai_danger_zone_alert, ai_next_step_suggestion, llm_provider
                ) VALUES (
                    :userId,
                    ST_SetSRID(ST_MakePoint(:startLng, :startLat), 4326),
                    CASE WHEN :hasEnd
                         THEN ST_SetSRID(ST_MakePoint(:endLng, :endLat), 4326)
                         END,
                    :targetDistanceKm,
                    :priorityConvenience, :priorityExercise, :priorityScenery,
                    :avoidDangerZonesApplied,
                    ST_Force2D(ST_SetSRID(ST_GeomFromGeoJSON(:routeGeoJson), 4326)),
                    :totalDistanceKm, :estimatedDurationMin, :totalAscentM, :totalDescentM,
                    :intensityLevel,
                    CAST(:waypointsJson AS jsonb), CAST(:passingDangerZonesJson AS jsonb),
                    :aiTitle, CAST(:aiHighlights AS jsonb), :aiCoachComment,
                    :aiDangerZoneAlert, :aiNextStepSuggestion, :llmProvider
                )
                RETURNING recommended_route_id, created_at, ST_AsGeoJSON(route_geom) AS route_geo_json
                """;

        boolean hasEnd = request.getEndLng() != null && request.getEndLat() != null;

        return jdbcClient.sql(sql)
                .param("userId", userId)
                .param("startLng", request.getStartLng())
                .param("startLat", request.getStartLat())
                .param("hasEnd", hasEnd)
                .param("endLng", hasEnd ? request.getEndLng() : request.getStartLng())
                .param("endLat", hasEnd ? request.getEndLat() : request.getStartLat())
                .param("targetDistanceKm", request.getTargetDistanceKm())
                .param("priorityConvenience", request.getPriorityConvenience())
                .param("priorityExercise", request.getPriorityExercise())
                .param("priorityScenery", request.getPriorityScenery())
                .param("avoidDangerZonesApplied", response.getAvoidDangerZonesApplied())
                .param("routeGeoJson", response.getRouteGeoJson())
                .param("totalDistanceKm", response.getTotalDistanceKm())
                .param("estimatedDurationMin", response.getEstimatedDurationMin())
                .param("totalAscentM", response.getTotalAscentM())
                .param("totalDescentM", response.getTotalDescentM())
                .param("intensityLevel", response.getIntensityLevel())
                .param("waypointsJson", toJson(response.getWaypoints()))
                .param("passingDangerZonesJson", toJson(response.getPassingDangerZones()))
                .param("aiTitle", response.getAiTitle())
                .param("aiHighlights", toJson(response.getAiHighlights()))
                .param("aiCoachComment", response.getAiCoachComment())
                .param("aiDangerZoneAlert", response.getAiDangerZoneAlert())
                .param("aiNextStepSuggestion", response.getAiNextStepSuggestion())
                .param("llmProvider", llmProvider)
                .query((rs, rowNum) -> new Saved(
                        rs.getLong("recommended_route_id"),
                        rs.getObject("created_at", OffsetDateTime.class),
                        rs.getString("route_geo_json")))
                .single();
    }

    /**
     * 저장된 추천 코스를 다시 읽는다.
     *
     * 형상은 ST_AsGeoJSON으로 되돌린다. 저장 시 Z를 떨궜으므로 좌표가 두 값으로 나온다. 지도에 그리는 데는 그편이 낫다.
     */
    public Optional<RouteRecommendResponseDTO> selectById(long recommendedRouteId) {
        String sql = """
                SELECT
                    recommended_route_id,
                    created_at,
                    total_distance_km,
                    estimated_duration_min,
                    total_ascent_m,
                    total_descent_m,
                    intensity_level,
                    ST_AsGeoJSON(route_geom) AS route_geo_json,
                    avoid_danger_zones_applied,
                    waypoints_json,
                    passing_danger_zones_json,
                    ai_title,
                    ai_highlights,
                    ai_coach_comment,
                    ai_danger_zone_alert,
                    ai_next_step_suggestion
                FROM recommended_route
                WHERE recommended_route_id = :id
                """;

        return jdbcClient.sql(sql)
                .param("id", recommendedRouteId)
                .query((rs, rowNum) -> {
                    RouteRecommendResponseDTO dto = new RouteRecommendResponseDTO();
                    dto.setRecommendedRouteId(rs.getLong("recommended_route_id"));
                    dto.setCreatedAt(rs.getObject("created_at", OffsetDateTime.class));
                    dto.setTotalDistanceKm(rs.getBigDecimal("total_distance_km"));
                    dto.setEstimatedDurationMin((Integer) rs.getObject("estimated_duration_min"));
                    dto.setTotalAscentM((Integer) rs.getObject("total_ascent_m"));
                    dto.setTotalDescentM((Integer) rs.getObject("total_descent_m"));
                    dto.setIntensityLevel(rs.getString("intensity_level"));
                    dto.setRouteGeoJson(rs.getString("route_geo_json"));
                    dto.setAvoidDangerZonesApplied(rs.getBoolean("avoid_danger_zones_applied"));
                    dto.setWaypoints(fromJsonList(rs.getString("waypoints_json"), WaypointDTO.class));
                    dto.setPassingDangerZones(
                            fromJsonList(rs.getString("passing_danger_zones_json"), PassingDangerZoneDTO.class));
                    dto.setAiTitle(rs.getString("ai_title"));
                    dto.setAiHighlights(fromJsonList(rs.getString("ai_highlights"), String.class));
                    dto.setAiCoachComment(rs.getString("ai_coach_comment"));
                    dto.setAiDangerZoneAlert(rs.getString("ai_danger_zone_alert"));
                    dto.setAiNextStepSuggestion(rs.getString("ai_next_step_suggestion"));
                    return dto;
                })
                .optional();
    }

    /** JSONB 컬럼에 넣을 문자열. null이면 컬럼도 NULL로 둔다 */
    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.error("JSONB 직렬화 실패: {}", value.getClass().getSimpleName(), e);
            throw new BusinessException(ErrorCode.COMMON_500);
        }
    }

    /**
     * JSONB 배열을 목록으로 되돌린다.
     *
     * 저장 시점의 스키마와 지금 클래스가 다를 수 있어 실패를 조용히 넘기지 않는다. 컬럼을 늘린 뒤 예전 행을 읽으면 여기서 드러난다.
     */
    private <T> List<T> fromJsonList(String json, Class<T> elementType) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, elementType));
        } catch (Exception e) {
            log.error("JSONB 역직렬화 실패: {} — {}", elementType.getSimpleName(), json, e);
            throw new BusinessException(ErrorCode.COMMON_500);
        }
    }

    /**
     * INSERT가 돌려주는 값.
     *
     * 형상까지 돌려주는 이유는 저장값과 응답을 일치시키기 위해서다. ORS 원본은 3차원인데 컬럼은 2D라 ST_Force2D로 Z를 떨구고 저장한다. 응답에 원본을 그대로 쓰면 POST로 받은 좌표와 GET으로 재조회한 좌표가 달라진다.
     */
    public record Saved(long recommendedRouteId, OffsetDateTime createdAt, String routeGeoJson) {
    }
}
